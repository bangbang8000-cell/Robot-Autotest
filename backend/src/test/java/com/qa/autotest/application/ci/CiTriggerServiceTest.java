package com.qa.autotest.application.ci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.ci.CiTriggerRecord;
import com.qa.autotest.domain.iam.ProjectApiToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CI 触发服务单测（PLN-03）。全部使用内存伪实现，不连库、不发 HTTP、不跑 pabot。
 */
class CiTriggerServiceTest {

    private CiTestSupport.FakePlanRepository plans;
    private CiTestSupport.StubSchedules schedules;
    private CiTestSupport.FakeCiTriggerRepository triggers;
    private CiTestSupport.RecordingAudit audit;
    private CiProperties properties;
    private CiTriggerService service;

    @BeforeEach
    void setUp() {
        plans = new CiTestSupport.FakePlanRepository();
        plans.put(CiTestSupport.plan(1L, CiTestSupport.PROJECT));
        schedules = new CiTestSupport.StubSchedules();
        triggers = new CiTestSupport.FakeCiTriggerRepository();
        audit = new CiTestSupport.RecordingAudit();
        properties = CiTestSupport.properties();
        service = new CiTriggerService(plans, schedules, triggers, audit,
                new TokenRateLimiter(properties, CiTestSupport.CLOCK), properties,
                CiTestSupport.CLOCK);
    }

    private static CiTriggerCommand command(String sha) {
        return new CiTriggerCommand(1L, sha, "refs/heads/main", null, "owner/repo");
    }

    @Test
    @DisplayName("新建成功：返回 runId、duplicated=false，并落一行 ci_trigger")
    void 新建成功() {
        ProjectApiToken token = CiTestSupport.token(11L, CiTestSupport.PROJECT, CiTestSupport.SCOPE);
        CiTriggerResult result = service.trigger(command("abc123def456"), token);

        assertEquals(false, result.duplicated());
        assertTrue(result.runId() != null && !result.runId().isBlank());
        assertEquals(1, triggers.all().size());
        assertEquals(result.runId(), triggers.all().get(0).runId());
        assertEquals(1L, schedules.plans.get(0).id());
    }

    @Test
    @DisplayName("幂等：同计划同 commit 第二次返回 duplicated=true 且不新建 run")
    void 重复同commit返回duplicated() {
        ProjectApiToken token = CiTestSupport.token(11L, CiTestSupport.PROJECT, CiTestSupport.SCOPE);
        CiTriggerResult first = service.trigger(command("abc123def456"), token);
        CiTriggerResult second = service.trigger(command("abc123def456"), token);

        assertFalse(first.duplicated());
        assertTrue(second.duplicated());
        assertEquals(first.runId(), second.runId());
        assertEquals(1, schedules.plans.size(), "只应建一个 run");
        assertEquals(1, triggers.all().size(), "只应有一行 ci_trigger");
        assertTrue(audit.has("CI_TRIGGER_DUPLICATED"));
    }

    @Test
    @DisplayName("不同 commit 新建第二个 run")
    void 不同commit新建() {
        ProjectApiToken token = CiTestSupport.token(11L, CiTestSupport.PROJECT, CiTestSupport.SCOPE);
        CiTriggerResult first = service.trigger(command("abc123def456"), token);
        CiTriggerResult second = service.trigger(command("fed654cba321"), token);

        assertNotEquals(first.runId(), second.runId());
        assertEquals(false, second.duplicated());
        assertEquals(2, schedules.plans.size());
        assertEquals(2, triggers.all().size());
    }

    @Test
    @DisplayName("scope 缺失：403")
    void scope缺失403() {
        ProjectApiToken token = CiTestSupport.token(12L, CiTestSupport.PROJECT, "runs:read");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.trigger(command("abc123def456"), token));
        assertEquals(ErrorCode.FORBIDDEN, ex.errorCode());
        assertEquals(0, triggers.all().size());
    }

    @Test
    @DisplayName("令牌项目与计划项目不匹配：403，不泄露计划是否存在")
    void 令牌项目不匹配403() {
        plans = new CiTestSupport.FakePlanRepository();
        plans.put(CiTestSupport.plan(2L, CiTestSupport.OTHER_PROJECT));
        service = new CiTriggerService(plans, schedules, triggers, audit,
                new TokenRateLimiter(properties, CiTestSupport.CLOCK), properties,
                CiTestSupport.CLOCK);

        ProjectApiToken token = CiTestSupport.token(11L, CiTestSupport.PROJECT, CiTestSupport.SCOPE);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.trigger(new CiTriggerCommand(2L, "abc123def456",
                        "refs/heads/main", null, "owner/repo"), token));
        assertEquals(ErrorCode.FORBIDDEN, ex.errorCode());
        assertEquals(0, schedules.plans.size());
        assertEquals(0, triggers.all().size());
    }

    @Test
    @DisplayName("限流：同一令牌第 11 次触发返回 429")
    void 限流第11次429() {
        ProjectApiToken token = CiTestSupport.token(11L, CiTestSupport.PROJECT, CiTestSupport.SCOPE);
        for (int i = 1; i <= 10; i++) {
            service.trigger(command("abc123def456"), token);
        }
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.trigger(command("abc123def456"), token));
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.errorCode());
    }

    @Test
    @DisplayName("build 缺省取 commitSha 前 8 位")
    void build缺省取前8位() {
        ProjectApiToken token = CiTestSupport.token(11L, CiTestSupport.PROJECT, CiTestSupport.SCOPE);
        service.trigger(command("abcdef0123456789"), token);

        CiTriggerRecord stored = triggers.all().get(0);
        assertEquals("abcdef01", stored.build());
        assertEquals("abcdef0123456789", stored.commitSha());
    }

    @Test
    @DisplayName("commitSha 非十六进制：400")
    void commitSha非法400() {
        ProjectApiToken token = CiTestSupport.token(11L, CiTestSupport.PROJECT, CiTestSupport.SCOPE);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.trigger(command("not-a-sha"), token));
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
    }

    @Test
    @DisplayName("审计含令牌名/计划/提交，且不含令牌明文")
    void 审计不含令牌明文() {
        ProjectApiToken token = CiTestSupport.token(11L, CiTestSupport.PROJECT, CiTestSupport.SCOPE);
        service.trigger(command("abc123def456"), token);

        assertTrue(audit.has("CI_TRIGGER"));
        assertTrue(audit.contains("token=ci-token-11"));
        assertTrue(audit.contains("plan=1"));
        assertTrue(audit.contains("commit=abc123def456"));
        assertFalse(audit.contains("Bearer"), "审计不得出现 Authorization 头");
    }

    @Test
    @DisplayName("认证失败信息不回显令牌明文")
    void 无效令牌不回显明文() {
        CiTestSupport.FakeTokenRepository tokenRepository = new CiTestSupport.FakeTokenRepository();
        CiTokenService tokenService = new CiTokenService(tokenRepository, CiTestSupport.CLOCK);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tokenService.authenticate("Bearer not-a-real-token-123"));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.errorCode());
        assertFalse(ex.getMessage().contains("not-a-real-token-123"));
    }
}

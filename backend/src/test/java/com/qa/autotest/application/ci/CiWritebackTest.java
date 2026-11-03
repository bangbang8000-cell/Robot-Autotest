package com.qa.autotest.application.ci;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.application.execution.RunCompletionListener;
import com.qa.autotest.application.execution.RunService;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.ci.CiTriggerRecord;
import com.qa.autotest.domain.ci.CiWritebackStatus;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CI 回写单测（PLN-03）。伪 HTTP 客户端只记录请求，不真实联网。
 */
class CiWritebackTest {

    private static final String RUN_ID = "RUN-CI-1";
    private static final String GITHUB_REPO = "huanchen811509-silence/Robot-Autotest";

    private CiTestSupport.FakeCiTriggerRepository triggers;
    private FakeStatusClient client;
    private FakeTokenSource tokenSource;
    private CiProperties properties;
    private CiTestSupport.RecordingAudit audit;
    private CiWritebackService service;

    @BeforeEach
    void setUp() {
        triggers = new CiTestSupport.FakeCiTriggerRepository();
        client = new FakeStatusClient();
        tokenSource = new FakeTokenSource("placeholder-token-for-test");
        properties = CiTestSupport.properties();
        audit = new CiTestSupport.RecordingAudit();
        service = new CiWritebackService(triggers, client, tokenSource, properties, audit);
    }

    private void givenTrigger(String repo) {
        triggers.put(new CiTriggerRecord(1L, CiTestSupport.PROJECT, 1L, "abc123def456",
                "refs/heads/main", "abc123de", repo, RUN_ID, CiWritebackStatus.PENDING, null,
                CiTestSupport.CLOCK.instant()));
    }

    private CiTriggerRecord stored() {
        return triggers.all().get(0);
    }

    @Test
    @DisplayName("passed（FINISHED）→ success，target_url 指向报告页")
    void passed写success() {
        givenTrigger(GITHUB_REPO);
        properties.getWriteback().setEnabled(true);

        RunRecord finished = CiTestSupport.run(1L, RUN_ID, CiTestSupport.PROJECT, RunState.FINISHED);
        service.onCompleted(finished);

        assertEquals(CiWritebackStatus.SUCCESS, stored().writebackStatus());
        assertNull(stored().writebackError());
        assertEquals(1, client.requests.size());
        assertEquals("success", client.requests.get(0).state());
        assertEquals(GITHUB_REPO, client.requests.get(0).repo());
        assertTrue(client.requests.get(0).targetUrl().endsWith("/reports/" + RUN_ID));
        assertTrue(audit.has("CI_WRITEBACK"));
    }

    @Test
    @DisplayName("failed（FAILED）→ failure")
    void failed写failure() {
        givenTrigger(GITHUB_REPO);
        properties.getWriteback().setEnabled(true);

        RunRecord failed = CiTestSupport.run(1L, RUN_ID, CiTestSupport.PROJECT, RunState.FAILED);
        service.onCompleted(failed);

        assertEquals(CiWritebackStatus.SUCCESS, stored().writebackStatus(),
                "回写调用成功时 writeback_status=success，与 run 结论无关");
        assertNull(stored().writebackError());
        assertEquals(1, client.requests.size());
        assertEquals("failure", client.requests.get(0).state());
    }

    @Test
    @DisplayName("回写失败不改 run 结论：只记 failed + writeback_error")
    void 回写失败不改运行状态() {
        givenTrigger(GITHUB_REPO);
        properties.getWriteback().setEnabled(true);
        client.failure = new IllegalStateException("github_status_io_error");
        RunRecord finished = CiTestSupport.run(1L, RUN_ID, CiTestSupport.PROJECT, RunState.FINISHED);

        assertDoesNotThrow(() -> service.onCompleted(finished));

        assertEquals(RunState.FINISHED, finished.state());
        assertEquals(CiWritebackStatus.FAILED, stored().writebackStatus());
        assertTrue(stored().writebackError().contains("github_error"));
        assertTrue(audit.contains("result=failed"));
    }

    @Test
    @DisplayName("默认 disabled：不发请求，只记 skipped")
    void 默认关闭不发请求() {
        givenTrigger(GITHUB_REPO);
        // CiProperties 默认 enabled=false
        service.onCompleted(CiTestSupport.run(1L, RUN_ID, CiTestSupport.PROJECT, RunState.FINISHED));

        assertEquals(0, client.requests.size(), "默认关闭时不得发 HTTP 请求");
        assertEquals(CiWritebackStatus.SKIPPED, stored().writebackStatus());
        assertEquals("disabled", stored().writebackError());
    }

    @Test
    @DisplayName("群晖 ssh 仓库：not_supported，不发请求且写审计")
    void 群晖不支持回写() {
        givenTrigger("ssh://synology/rf-tests.git");
        properties.getWriteback().setEnabled(true);

        service.onCompleted(CiTestSupport.run(1L, RUN_ID, CiTestSupport.PROJECT, RunState.FINISHED));

        assertEquals(0, client.requests.size(), "群晖不得发起任何 HTTP 回写");
        assertEquals(CiWritebackStatus.NOT_SUPPORTED, stored().writebackStatus());
        assertTrue(stored().writebackError().contains("synology"));
        assertTrue(audit.contains("result=not_supported"));
    }

    @Test
    @DisplayName("未配置回写令牌：skipped 且不发请求")
    void 缺令牌只记skipped() {
        givenTrigger(GITHUB_REPO);
        properties.getWriteback().setEnabled(true);
        tokenSource.value = null;

        service.onCompleted(CiTestSupport.run(1L, RUN_ID, CiTestSupport.PROJECT, RunState.FINISHED));

        assertEquals(0, client.requests.size());
        assertEquals(CiWritebackStatus.SKIPPED, stored().writebackStatus());
        assertEquals("token_missing", stored().writebackError());
    }

    @Test
    @DisplayName("非 CI 触发的 run：没有 ci_trigger 记录时静默跳过")
    void 非CI运行跳过() {
        service.onCompleted(CiTestSupport.run(1L, "RUN-MANUAL", CiTestSupport.PROJECT,
                RunState.FINISHED));
        assertEquals(0, client.requests.size());
        assertEquals(0, triggers.all().size());
    }

    @Test
    @DisplayName("RunService 钩子：终态触发监听器、异常被吞掉且不影响 run 状态")
    void 终态钩子触发且异常被吞() {
        CiTestSupport.FakeRunRepository runs = new CiTestSupport.FakeRunRepository();
        runs.put(CiTestSupport.run(1L, RUN_ID, CiTestSupport.PROJECT, RunState.RUNNING));
        List<RunRecord> notified = new ArrayList<>();
        List<RunCompletionListener> listeners = List.of(
                notified::add,
                run -> {
                    throw new IllegalStateException("listener boom");
                });
        RunService runService = CiTestSupport.runService(runs, audit, listeners);

        TenantContext.set(CiTestSupport.OPERATOR, "tester", CiTestSupport.PROJECT);
        try {
            RunRecord finished = runService.transition(RUN_ID, RunState.FINISHED,
                    RunService.TransitionContext.plain());
            assertEquals(RunState.FINISHED, finished.state());
            assertEquals(RunState.FINISHED, runs.current.state());
            assertEquals(1, notified.size());
            assertEquals(RunState.FINISHED, notified.get(0).state());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("RunService 钩子：中间态不触发监听器")
    void 中间态不触发() {
        CiTestSupport.FakeRunRepository runs = new CiTestSupport.FakeRunRepository();
        runs.put(CiTestSupport.run(1L, RUN_ID, CiTestSupport.PROJECT, RunState.QUEUED));
        List<RunRecord> notified = new ArrayList<>();
        RunService runService = CiTestSupport.runService(runs, audit, List.of(notified::add));

        TenantContext.set(CiTestSupport.OPERATOR, "tester", CiTestSupport.PROJECT);
        try {
            runService.transition(RUN_ID, RunState.PREPARING, RunService.TransitionContext.plain());
            assertEquals(0, notified.size());
        } finally {
            TenantContext.clear();
        }
    }

    /** 伪 HTTP 客户端：记录请求，可配置返回值或抛异常；不联网。 */
    static final class FakeStatusClient implements CiStatusClient {

        final List<CiStatusRequest> requests = new ArrayList<>();
        CiStatusResponse response = new CiStatusResponse(201, "{\"ok\":true}");
        RuntimeException failure;

        @Override
        public CiStatusResponse post(CiStatusRequest request, String token) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }

    static final class FakeTokenSource implements GitHubTokenSource {

        String value;

        FakeTokenSource(String value) {
            this.value = value;
        }

        @Override
        public String token() {
            return value;
        }
    }
}

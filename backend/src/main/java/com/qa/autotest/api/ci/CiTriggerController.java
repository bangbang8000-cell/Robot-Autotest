package com.qa.autotest.api.ci;

import com.qa.autotest.application.ci.CiTokenService;
import com.qa.autotest.application.ci.CiTriggerCommand;
import com.qa.autotest.application.ci.CiTriggerResult;
import com.qa.autotest.application.ci.CiTriggerService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.iam.ProjectApiToken;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CI 触发接口（PLN-03）：{@code POST /api/ci/trigger}。
 *
 * <p><b>不走普通用户会话</b>：{@code /api/ci/**} 已在 WebConfig 里从 AuthInterceptor 放行，
 * 由 {@link CiTokenService} 用项目 API token（{@code Authorization: Bearer <token>}，
 * scope 含 {@code ci:trigger}）自行认证。
 *
 * <p>成功与重复命中都返回 <b>202 Accepted</b>：run 已落库，执行异步进行，接口不等待结果。
 */
@RestController
@RequestMapping("/api/ci")
public class CiTriggerController {

    private final CiTokenService tokens;
    private final CiTriggerService triggers;

    public CiTriggerController(CiTokenService tokens, CiTriggerService triggers) {
        this.tokens = tokens;
        this.triggers = triggers;
    }

    @PostMapping("/trigger")
    public ResponseEntity<ApiResponse<CiTriggerResult>> trigger(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) CiTriggerRequest body) {
        ProjectApiToken token = tokens.authenticate(authorization);
        if (body == null || body.planId() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "planId 不能为空");
        }
        CiTriggerResult result = triggers.trigger(new CiTriggerCommand(
                        body.planId(), body.commitSha(), body.ref(), body.build(), body.repo()),
                token);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(result));
    }

    /**
     * 请求体：{@code {planId, commitSha, ref, build?, repo?}}。
     * {@code build} 缺省由服务端取 commitSha 前 8 位。
     */
    public record CiTriggerRequest(Long planId,
                                   String commitSha,
                                   String ref,
                                   String build,
                                   String repo) {
    }
}

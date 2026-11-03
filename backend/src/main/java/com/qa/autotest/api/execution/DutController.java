package com.qa.autotest.api.execution;

import com.qa.autotest.application.asset.CredentialService;
import com.qa.autotest.application.execution.DutCollectorService;
import com.qa.autotest.application.execution.DutOutcome;
import com.qa.autotest.application.execution.DutTarget;
import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DUT 采集入口（EXE-08）。
 *
 * <p>凭据两种来源：{@code credentialId} 非空时由服务层解密（带权限校验与审计），
 * 否则用请求体 {@code password}；两者都为空即公钥模式。请求体口令不会被回显到任何响应。
 */
@RestController
@RequestMapping("/api/execution/dut")
public class DutController {

    private final DutCollectorService collector;
    private final CredentialService credentials;
    private final AccessControlService access;
    private final ExecutionProperties props;

    public DutController(DutCollectorService collector, CredentialService credentials,
                         AccessControlService access, ExecutionProperties props) {
        this.collector = collector;
        this.credentials = credentials;
        this.access = access;
        this.props = props;
    }

    @PostMapping("/collect")
    public ApiResponse<DutOutcome> collect(@RequestBody DutCollectRequest request) {
        access.bindProject(request.projectId());
        access.require(PermissionResource.ASSET, PermissionAction.READ);

        String host = requireText(request.host(), "host");
        String user = requireText(request.user(), "user");
        int port = request.port() <= 0 ? DutTarget.DEFAULT_PORT : request.port();
        String password = request.credentialId() == null
                ? request.password()
                : credentials.resolveSecret(request.projectId(), request.credentialId());
        String jumpHost = orDefault(request.jumpHost(), props.getDut().getJumpHost());
        String jumpUser = orDefault(request.jumpUser(), props.getDut().getJumpUser());

        DutTarget target = new DutTarget(host, port, user, password,
                request.privateKeyPath(), jumpHost, jumpUser);
        return ApiResponse.ok(collector.collectOutcome(target, request.iface()));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, field + " 不能为空");
        }
        return value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record DutCollectRequest(long projectId,
                                    String host,
                                    int port,
                                    String user,
                                    Long credentialId,
                                    String password,
                                    String privateKeyPath,
                                    String jumpHost,
                                    String jumpUser,
                                    String iface) {
    }
}

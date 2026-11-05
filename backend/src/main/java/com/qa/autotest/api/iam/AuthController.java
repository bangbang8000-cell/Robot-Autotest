package com.qa.autotest.api.iam;

import com.qa.autotest.api.AuthInterceptor;
import com.qa.autotest.application.iam.AuthService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.iam.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录 / 注销 / 当前用户（IAM-02）。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserSummary user,
                                boolean passwordExpired) {
    }

    public record RenewResponse(Instant expiresAt) {
    }

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        AuthService.LoginResult result = auth.login(request.username(), request.password());
        return ApiResponse.ok(new LoginResponse(result.token(), result.expiresAt(),
                result.user(), result.passwordExpired()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        Object token = request.getAttribute(AuthInterceptor.ATTR_TOKEN);
        auth.logout(token == null ? null : token.toString());
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserSummary> me() {
        return ApiResponse.ok(auth.me(TenantContext.requireUserId()));
    }

    /** 会话续期（FR-v0.2-04）：前端在过期前 5 分钟调用，把有效期再推一个 TTL。 */
    @PostMapping("/renew")
    public ApiResponse<RenewResponse> renew(HttpServletRequest request) {
        Object token = request.getAttribute(AuthInterceptor.ATTR_TOKEN);
        return ApiResponse.ok(new RenewResponse(auth.renew(token == null ? null : token.toString())));
    }
}

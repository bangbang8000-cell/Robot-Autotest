package com.qa.autotest.api;

import com.qa.autotest.application.iam.AuthService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.iam.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器（IAM-02 / IAM-04）。
 *
 * <p>只做三件事：解析 Bearer 令牌 → 写入 {@link TenantContext} → 请求结束清理。
 * 权限判定留给应用层（{@code AccessControlService}），避免拦截器里堆业务规则。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 请求属性键：已认证令牌（注销接口复用）。 */
    public static final String ATTR_TOKEN = "authToken";

    private final AuthService auth;

    public AuthInterceptor(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String token = bearerToken(request);
        if (token == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "缺少访问令牌");
        }
        AppUser user = auth.resolveUser(token)
                .filter(AppUser::enabled)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "令牌无效或已过期"));
        TenantContext.set(user.id(), user.username(), parseProjectId(request.getHeader("X-Project-Id")));
        request.setAttribute(ATTR_TOKEN, token);
        auth.touch(token);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static Long parseProjectId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "X-Project-Id 不是数字");
        }
    }
}

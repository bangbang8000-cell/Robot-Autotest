package com.qa.autotest.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web 装配：认证拦截器覆盖 /api/**，放行探活与登录。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // /api/print/** 用一次性 token 授权（签发时已校验项目权限），
                // 无头浏览器访问打印页时没有登录态，不能走 Bearer 拦截。
                // /api/ci/** 用项目 API token（Bearer，scope=ci:trigger）自认证，不走用户会话。
                .excludePathPatterns(
                        "/api/_ping", "/api/auth/login", "/api/print/**", "/api/ci/**");
    }
}

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
                // /api/health 是就绪探针（ENVX-02）：容器 / 统一入口探活时没有登录态，
                // 且它内部只做 SELECT 1，不读任何业务数据。
                // /api/nodes/*/heartbeat（NODX-01）用节点令牌（X-Node-Token 头，服务端比哈希）
                // 自认证——agent 上没有用户登录态；注册/列表仍走管理员会话，不排除。
                .excludePathPatterns(
                        "/api/_ping", "/api/health", "/api/auth/login",
                        "/api/print/**", "/api/ci/**", "/api/nodes/*/heartbeat",
                // NODX-03：agent 无用户会话——节点收任务（node profile）与主控收回传都以 X-Node-Token / 任务签名自认证
                "/api/agent/**");
    }
}

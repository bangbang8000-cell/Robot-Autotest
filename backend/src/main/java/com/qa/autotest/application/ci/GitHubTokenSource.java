package com.qa.autotest.application.ci;

/**
 * GitHub 回写令牌来源（PLN-03）。
 *
 * <p>唯一实现从环境变量 {@code GITHUB_STATUS_TOKEN} 读取；测试注入伪实现，
 * 生产路径<b>不读取配置文件、不落库、不落日志</b>。未配置时返回 null。
 */
public interface GitHubTokenSource {

    String token();
}

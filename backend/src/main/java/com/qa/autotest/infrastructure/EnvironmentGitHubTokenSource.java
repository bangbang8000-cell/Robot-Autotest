package com.qa.autotest.infrastructure;

import com.qa.autotest.application.ci.GitHubTokenSource;
import org.springframework.stereotype.Component;

/**
 * 从环境变量 {@code GITHUB_STATUS_TOKEN} 读取回写令牌（PLN-03）。
 *
 * <p>只读环境变量：不读配置文件、不落库、不打印。未配置时返回 null，回写侧记为 skipped。
 */
@Component
public class EnvironmentGitHubTokenSource implements GitHubTokenSource {

    static final String ENV_NAME = "GITHUB_STATUS_TOKEN";

    @Override
    public String token() {
        return System.getenv(ENV_NAME);
    }
}

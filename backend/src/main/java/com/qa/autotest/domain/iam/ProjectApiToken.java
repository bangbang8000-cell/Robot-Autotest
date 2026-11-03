package com.qa.autotest.domain.iam;

import java.util.Arrays;
import java.util.List;

/**
 * 项目 API 令牌视图（PLN-03）。
 *
 * <p>只承载认证后需要的最小字段：令牌明文与哈希都不进入本对象，避免在应用层被日志带出。
 *
 * @param projectId 绑定的项目；为 null 表示令牌未绑定项目，调用方必须拒绝
 * @param scopes    逗号分隔的 scope 原文（空串表示无任何 scope）
 */
public record ProjectApiToken(long id,
                              long userId,
                              String name,
                              Long projectId,
                              String scopes) {

    /** 判断令牌是否具备指定 scope。空串不等于「全部 scope」。 */
    public boolean hasScope(String scope) {
        if (scope == null || scope.isBlank() || scopes == null || scopes.isBlank()) {
            return false;
        }
        List<String> granted = Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
        return granted.contains(scope.trim());
    }

    public boolean boundToProject() {
        return projectId != null;
    }
}

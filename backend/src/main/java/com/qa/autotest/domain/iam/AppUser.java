package com.qa.autotest.domain.iam;

import java.time.Instant;

/**
 * 平台用户（纯 POJO，不带任何框架注解）。
 *
 * <p>{@code passwordHash} 只在认证路径读取；任何接口响应都必须经过脱敏转换，不得直接序列化本对象。
 */
public record AppUser(
        Long id,
        String username,
        String displayName,
        String email,
        String passwordHash,
        Long orgId,
        boolean enabled,
        Instant passwordExpiresAt,
        int failedLoginCount,
        Instant lockedUntil,
        Instant lastLoginAt) {

    /** 是否处于登录锁定期。 */
    public boolean lockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}

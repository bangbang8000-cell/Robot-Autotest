package com.qa.autotest.domain.iam;

import java.time.Instant;
import java.util.List;

/** 用户对外视图：不含 passwordHash，任何接口只能返回这个。 */
public record UserSummary(
        Long id,
        String username,
        String displayName,
        String email,
        Long orgId,
        boolean enabled,
        Instant passwordExpiresAt,
        Instant lastLoginAt,
        List<String> roles) {

    public static UserSummary withoutRoles(AppUser user) {
        return new UserSummary(user.id(), user.username(), user.displayName(), user.email(),
                user.orgId(), user.enabled(), user.passwordExpiresAt(), user.lastLoginAt(),
                List.of());
    }

    public UserSummary withRoles(List<String> roleCodes) {
        return new UserSummary(id, username, displayName, email, orgId, enabled,
                passwordExpiresAt, lastLoginAt, List.copyOf(roleCodes));
    }
}

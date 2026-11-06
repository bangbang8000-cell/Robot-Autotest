package com.qa.autotest.domain.group;

import java.util.Locale;

/** 有效角色（IAMX-02）：项目直接角色与组角色合并后取最高，单点判定在这里。 */
public enum EffectiveRole {
    NONE(0),
    VIEWER(1),
    ENGINEER(2),
    PM(3),
    ADMIN(4);

    private final int rank;

    EffectiveRole(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static EffectiveRole parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        for (EffectiveRole role : values()) {
            if (role.name().equals(upper)) {
                return role;
            }
        }
        return NONE;
    }

    public static EffectiveRole highest(EffectiveRole direct, EffectiveRole grouped) {
        return direct.rank() >= grouped.rank() ? direct : grouped;
    }

    /** 动作白名单：跨项目（NONE）一律拒绝，不依赖调用方自己记得判断。 */
    public boolean permits(String action) {
        if (this == NONE) {
            return false;
        }
        return switch (action == null ? "" : action.toLowerCase(Locale.ROOT)) {
            case "read" -> rank() >= VIEWER.rank();
            case "execute" -> rank() >= ENGINEER.rank();
            case "manage" -> rank() >= PM.rank();
            case "admin" -> rank() >= ADMIN.rank();
            default -> false;
        };
    }
}

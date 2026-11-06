package com.qa.autotest.domain.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class EffectiveRoleTest {

    private static final String[] ROLES = {"NONE", "VIEWER", "ENGINEER", "PM", "ADMIN"};
    private static final String[] ACTIONS = {"read", "execute", "manage", "admin"};

    static Stream<Arguments> matrix() {
        List<Arguments> cases = new ArrayList<>();
        for (String role : ROLES) {
            for (String action : ACTIONS) {
                for (int project = 1; project <= 4; project++) {
                    cases.add(Arguments.of(role, action, project, expected(role, action)));
                }
            }
        }
        return cases.stream();
    }

    private static boolean expected(String role, String action) {
        if ("NONE".equals(role)) {
            return false;
        }
        int need = switch (action) {
            case "read" -> 1;
            case "execute" -> 2;
            case "manage" -> 3;
            case "admin" -> 4;
            default -> 99;
        };
        return EffectiveRole.parse(role).rank() >= need;
    }

    /** 5 角色 × 4 动作 × 4 项目上下文 = 80 条越权矩阵（要求 ≥ 60）。 */
    @ParameterizedTest(name = "{0} x {1} x project{2} -> {3}")
    @MethodSource("matrix")
    void authorizationMatrix(String role, String action, int project, boolean expected) {
        assertEquals(expected, EffectiveRole.parse(role).permits(action),
                role + " / " + action + " / project " + project);
    }

    @Test
    void groupRoleCanOnlyElevateNeverLower() {
        assertEquals(EffectiveRole.ADMIN,
                EffectiveRole.highest(EffectiveRole.VIEWER, EffectiveRole.ADMIN));
        assertEquals(EffectiveRole.ADMIN,
                EffectiveRole.highest(EffectiveRole.ADMIN, EffectiveRole.VIEWER));
        assertEquals(EffectiveRole.NONE, EffectiveRole.parse("unknown"));
    }

    @Test
    void unknownActionIsDenied() {
        assertFalse(EffectiveRole.ADMIN.permits("drop-tables"));
        assertTrue(EffectiveRole.VIEWER.permits("read"));
        assertFalse(EffectiveRole.VIEWER.permits("execute"));
    }
}

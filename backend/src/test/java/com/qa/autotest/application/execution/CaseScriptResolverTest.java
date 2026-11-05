package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.RobotFileRepository;
import com.qa.autotest.domain.caseasset.FileNode;
import com.qa.autotest.domain.caseasset.RobotFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXEC-02 绑定解析：标签 {@code case:<code>} 优先，测试名等于编号兜底，未命中不入结果。
 */
class CaseScriptResolverTest {

    private static final long PROJECT = 7L;

    @Test
    @DisplayName("节区解析：中英节区名、注释、缩进关键字、[Tags]（大小写不敏感）")
    void 节区解析() {
        List<CaseScriptResolver.ScriptTest> tests = CaseScriptResolver.scan("""
                *** Settings ***
                Documentation    平台说明
                *** Variables ***
                ${X}    y

                *** Test Cases ***
                # 注释不算用例
                压测-A
                    [Tags]    perf    case:PERF-004
                    Log    hi
                压测-B
                    [tags]    case:PERF-005
                    Log    ok

                *** Keywords ***
                压测-A
                    No Operation

                *** 测试用例 ***
                压测-C
                    Log    ok
                """);

        assertEquals(3, tests.size());
        assertEquals("压测-A", tests.get(0).name());
        assertEquals(Set.of("perf", "case:PERF-004"), tests.get(0).tags());
        assertEquals(Set.of("case:PERF-005"), tests.get(1).tags());
        assertEquals("压测-C", tests.get(2).name());
    }

    @Test
    @DisplayName("绑定解析：标签命中两条，测试名兜底命中一条，未命中不返回")
    void 绑定解析() {
        Map<String, String> files = Map.of(
                "suites/fallback.robot", """
                        *** Test Cases ***
                        PERF-001
                            Log    ok
                        """,
                "suites/tagged.robot", """
                        *** Test Cases ***
                        200G 带宽压测
                            [Tags]    case:PERF-002
                            Log    ok
                        """);
        CaseScriptResolver resolver = new CaseScriptResolver(repo(files));

        Map<String, List<CaseScriptResolver.Binding>> bindings =
                resolver.resolve(PROJECT, Set.of("PERF-001", "PERF-002", "PERF-999"));

        assertEquals(2, bindings.size());
        assertEquals(List.of(new CaseScriptResolver.Binding("suites/fallback.robot", "PERF-001")),
                bindings.get("PERF-001"));
        assertEquals(List.of(new CaseScriptResolver.Binding("suites/tagged.robot", "200G 带宽压测")),
                bindings.get("PERF-002"));
        assertTrue(bindings.get("PERF-999") == null);
    }

    private static RobotFileRepository repo(Map<String, String> files) {
        return (RobotFileRepository) Proxy.newProxyInstance(
                RobotFileRepository.class.getClassLoader(), new Class<?>[]{RobotFileRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "nodes" -> files.keySet().stream()
                            .map(path -> new FileNode(path, false, 0, Instant.EPOCH)).toList();
                    case "findByPath" -> {
                        String path = (String) args[1];
                        String content = files.get(path);
                        yield content == null ? Optional.empty()
                                : Optional.of(new RobotFile(1L, PROJECT, path, content, "sha", 1));
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (Optional.class.equals(type)) {
            return Optional.empty();
        }
        if (List.class.isAssignableFrom(type)) {
            return List.of();
        }
        return null;
    }
}

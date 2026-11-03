package com.qa.autotest.application.execution;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** pabot 命令装配：argv 结构与环境污染面固定，任何一项偏移都会导致平台侧执行环境不一致。 */
class PabotCommandBuilderTest {

    private final PabotCommandBuilder builder = new PabotCommandBuilder();

    @TempDir
    Path tmp;

    private ExecutionProperties props() {
        ExecutionProperties props = new ExecutionProperties();
        props.setVenvBin(tmp.resolve("venv").resolve("bin").toString());
        props.setWorkspaceRoot(tmp.resolve("ws").toString());
        props.setArtifactsRoot(tmp.resolve("artifacts").toString());
        return props;
    }

    @Test
    void 命令包含必需参数与固定环境变量() {
        ExecutionProperties props = props();
        PabotCommandBuilder.PabotCommand command = builder.build(
                new PabotCommandBuilder.ShardSpec(0,
                        List.of("suites/a.robot", "suites/b.robot"), 2, "smoke", "run-X"),
                props, tmp.resolve("artifacts"));

        List<String> argv = command.argv();
        String venvBin = tmp.resolve("venv").resolve("bin").toString();
        assertEquals(venvBin + java.io.File.separator + "pabot", argv.get(0));
        assertEquals("2", argv.get(argv.indexOf("--processes") + 1));
        assertEquals("run-X", argv.get(argv.indexOf("--name") + 1));
        assertEquals("smoke", argv.get(argv.indexOf("--include") + 1));
        assertTrue(argv.contains("suites/a.robot"));
        assertTrue(argv.contains("suites/b.robot"));

        assertEquals("Asia/Shanghai", command.environment().get("TZ"));
        assertEquals("C.UTF-8", command.environment().get("LANG"));
        assertEquals("1", command.environment().get("PYTHONUNBUFFERED"));
        assertTrue(command.environment().get("PATH").startsWith(venvBin));
        assertEquals(tmp.resolve("artifacts").resolve("shard-0"), command.outputDir());
    }

    @Test
    void 没有标签时不追加_include() {
        PabotCommandBuilder.PabotCommand command = builder.build(
                new PabotCommandBuilder.ShardSpec(1, List.of("suites/a.robot"), 1, null, "run-Y"),
                props(), tmp.resolve("artifacts"));
        assertTrue(!command.argv().contains("--include"));
        assertEquals(tmp.resolve("artifacts").resolve("shard-1"), command.outputDir());
    }

    @Test
    void suite_为空拒绝装配() {
        assertThrows(IllegalArgumentException.class, () -> builder.build(
                new PabotCommandBuilder.ShardSpec(0, List.of(), 1, null, "run-Z"),
                props(), tmp.resolve("artifacts")));
    }

    @Test
    void processes_越界拒绝装配() {
        assertThrows(IllegalArgumentException.class, () -> builder.build(
                new PabotCommandBuilder.ShardSpec(0, List.of("suites/a.robot"), 0, null, "run-Z"),
                props(), tmp.resolve("artifacts")));
        assertThrows(IllegalArgumentException.class, () -> builder.build(
                new PabotCommandBuilder.ShardSpec(0, List.of("suites/a.robot"), 2, null, "run-Z"),
                props(), tmp.resolve("artifacts")));
    }
}

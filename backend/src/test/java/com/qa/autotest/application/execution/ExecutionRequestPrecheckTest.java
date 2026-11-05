package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.RobotFileRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.caseasset.RobotFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** EXEC-04 / EXEC-05 请求期预检：数据库优先、磁盘回落、路径存在性与测试名重名。 */
class ExecutionRequestPrecheckTest {

    private static final long PROJECT = ExecutionTestSupport.PROJECT;

    @TempDir
    Path workspace;

    private ExecutionProperties props;

    @BeforeEach
    void setUp() {
        props = ExecutionTestSupport.props();
        props.setWorkspaceRoot(workspace.toString());
    }

    @Test
    @DisplayName("数据库为空回落磁盘：文件与目录都存在放行")
    void 磁盘回落路径存在放行() throws IOException {
        writeSuite("suites/a.robot", "PERF-001");

        precheck(List.of()).requireSuitesExist(PROJECT, List.of("suites/a.robot", "suites"));
    }

    @Test
    @DisplayName("数据库为空回落磁盘：路径不存在 400，消息带工作区与缺失路径")
    void 磁盘回落路径缺失拒绝() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> precheck(List.of()).requireSuitesExist(
                        PROJECT, List.of("suites/a.robot", "suites/missing.robot")));

        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
        assertTrue(ex.getMessage().contains("不存在"), ex.getMessage());
        assertTrue(ex.getMessage().contains("suites/a.robot"), ex.getMessage());
        assertTrue(ex.getMessage().contains("suites/missing.robot"), ex.getMessage());
        assertTrue(ex.getMessage().contains(workspace.toString()), ex.getMessage());
    }

    @Test
    @DisplayName("数据库有脚本：磁盘没有也算存在，以数据库为准")
    void 数据库优先放行() {
        precheck(List.of(script("suites/a.robot", "PERF-001")))
                .requireSuitesExist(PROJECT, List.of("suites", "suites/a.robot"));
    }

    @Test
    @DisplayName("数据库有脚本：路径缺失即 400，且不回退磁盘")
    void 数据库优先拒绝() throws IOException {
        writeSuite("suites/b.robot", "PERF-002");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> precheck(List.of(script("suites/a.robot", "PERF-001")))
                        .requireSuitesExist(PROJECT, List.of("suites/b.robot")));

        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
        assertTrue(ex.getMessage().contains("robot_file"), ex.getMessage());
        assertTrue(ex.getMessage().contains("suites/b.robot"), ex.getMessage());
    }

    @Test
    @DisplayName("数据库脚本测试名跨所选套件重名：400，列出名字与套件")
    void 数据库测试名重名拒绝() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> precheck(List.of(
                        script("suites/a.robot", "PERF-001"),
                        script("suites/b.robot", "PERF-001")))
                        .requireUnambiguousTestNames(PROJECT,
                                List.of("suites/a.robot", "suites/b.robot"), List.of("PERF-001")));

        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
        assertTrue(ex.getMessage().contains("重复"), ex.getMessage());
        assertTrue(ex.getMessage().contains("suites/a.robot"), ex.getMessage());
        assertTrue(ex.getMessage().contains("suites/b.robot"), ex.getMessage());
    }

    @Test
    @DisplayName("选择目录套件时也扫描目录下全部 .robot")
    void 目录套件重名拒绝() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> precheck(List.of(
                        script("suites/a.robot", "PERF-001"),
                        script("suites/sub/b.robot", "PERF-001")))
                        .requireUnambiguousTestNames(PROJECT, List.of("suites"), List.of("PERF-001")));

        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
        assertTrue(ex.getMessage().contains("重复"), ex.getMessage());
    }

    @Test
    @DisplayName("数据库脚本测试名唯一：放行")
    void 数据库测试名唯一放行() {
        precheck(List.of(
                script("suites/a.robot", "PERF-001"),
                script("suites/b.robot", "PERF-002")))
                .requireUnambiguousTestNames(PROJECT,
                        List.of("suites/a.robot", "suites/b.robot"), List.of("PERF-001"));
    }

    @Test
    @DisplayName("无测试名清单（套件级执行）：不扫重名")
    void 空清单直接放行() {
        precheck(List.of(
                script("suites/a.robot", "PERF-001"),
                script("suites/b.robot", "PERF-001")))
                .requireUnambiguousTestNames(PROJECT,
                        List.of("suites/a.robot", "suites/b.robot"), List.of());
    }

    private ExecutionRequestPrecheck precheck(List<RobotFile> scripts) {
        RobotFileRepository repo = ExecutionTestSupport.scriptRepo(scripts);
        return new ExecutionRequestPrecheck(repo, props, new NioFileSystemProbe());
    }

    private static RobotFile script(String path, String testName) {
        return new RobotFile(1L, PROJECT, path,
                "*** Test Cases ***\n" + testName + "\n    Log    ok\n", "sha", 1);
    }

    private void writeSuite(String path, String testName) throws IOException {
        Path file = workspace.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "*** Test Cases ***\n" + testName + "\n    Log    ok\n");
    }
}

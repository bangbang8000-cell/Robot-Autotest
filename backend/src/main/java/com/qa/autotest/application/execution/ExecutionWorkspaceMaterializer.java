package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.RobotFileRepository;
import com.qa.autotest.domain.caseasset.RobotFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 执行工作区落盘（EXEC-05）。
 *
 * <p>ADR-0005：{@code robot_file.content} 是脚本唯一真源。执行前把该项目全部脚本按原路径
 * 写进本次 run 的独立工作区 {@code <artifacts>/<runId>/workspace}，pabot 以该目录为工作目录，
 * 从而消除「绑定读数据库、执行读磁盘」两套来源不一致的问题，也让并发 run 互不污染。
 *
 * <p>数据库无脚本时返回空：兼容仍在磁盘维护脚本的项目（计划 / CI 触发场景），
 * 由调用方回落到 {@code execution.workspace-root}。
 */
@Service
public class ExecutionWorkspaceMaterializer {

    /** 工作区目录名，放在 run 产物目录内，与其他 run 隔离。 */
    public static final String WORKSPACE_DIR = "workspace";

    private final RobotFileRepository files;

    public ExecutionWorkspaceMaterializer(RobotFileRepository files) {
        this.files = files;
    }

    /**
     * 把项目脚本落盘到本次 run 的独立工作区。
     *
     * @return 落盘后的工作区目录；项目在数据库中没有任何脚本时返回 {@link Optional#empty()}
     */
    public Optional<Path> materialize(long projectId, Path runDir) throws IOException {
        List<RobotFile> scripts = files.listAll(projectId);
        if (scripts.isEmpty()) {
            return Optional.empty();
        }
        Path workspace = runDir.resolve(WORKSPACE_DIR);
        Files.createDirectories(workspace);
        for (RobotFile file : scripts) {
            Path target = workspace.resolve(safePath(file.path()));
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, file.content() == null ? "" : file.content(),
                    StandardCharsets.UTF_8);
        }
        return Optional.of(workspace);
    }

    /** 只接受相对路径，拒绝绝对路径与 {@code ..} 段（数据库行的二次防线）。 */
    static String safePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("脚本路径为空");
        }
        String normalized = path.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("脚本路径必须是相对路径: " + path);
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("脚本路径不允许包含 ..: " + path);
            }
        }
        return normalized;
    }
}

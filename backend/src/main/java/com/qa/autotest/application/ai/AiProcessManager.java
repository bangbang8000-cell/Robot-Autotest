package com.qa.autotest.application.ai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * CLI 命令构造 / 超时 / 终止（AIX-02）。
 *
 * <p>本类只负责「命令长什么样、超时是多少、怎么终止」；真正起子进程须经
 * {@link AiPanelService} 的二次确认闸门，避免 AI 动作绕过审计直接跑。
 */
@Component
public class AiProcessManager {

    public static final int DEFAULT_TIMEOUT_SECONDS = 60;

    public List<String> buildCommand(String cli, String promptFile) {
        List<String> argv = new ArrayList<>();
        argv.add(cli);
        argv.add("--print");
        argv.add("--output-format");
        argv.add("text");
        argv.add("--prompt-file");
        argv.add(promptFile);
        return List.copyOf(argv);
    }

    public String encoding() {
        return StandardCharsets.UTF_8.name();
    }

    public int timeoutSeconds() {
        return DEFAULT_TIMEOUT_SECONDS;
    }

    /** 一键终止：先后代再自身，复用 W3 的进程树语义。 */
    public void terminate(ProcessHandle root) {
        if (root == null || !root.isAlive()) {
            return;
        }
        root.descendants().forEach(child -> child.destroyForcibly());
        root.destroyForcibly();
    }
}

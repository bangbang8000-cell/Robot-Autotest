package com.qa.autotest.application.execution;

import java.time.Duration;

/**
 * SSH 命令执行抽象（EXE-08）。
 *
 * <p>实现可以是系统 OpenSSH（{@code SystemSshClient}），也可以是测试回放（{@code ReplaySshClient}）；
 * 调用方只关心退出码与两路输出，不关心传输细节。
 */
public interface SshClient {

    /**
     * 在目标机上执行一条命令。
     *
     * @param target        目标（口令只在实现内部的进程环境里出现，不得进 argv / 日志）
     * @param command       远程 shell 命令原文
     * @param connectTimeout 建连超时
     * @param commandTimeout 单命令总超时
     * @return 退出码、stdout、stderr 与耗时
     */
    CommandResult execute(DutTarget target, String command,
                          Duration connectTimeout, Duration commandTimeout);

    /** 单条命令的执行结果。stderr 只含诊断文本，绝不含口令。 */
    record CommandResult(int exitCode, String stdout, String stderr, Duration elapsed) {
    }
}

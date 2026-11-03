package com.qa.autotest.application.execution;

/**
 * DUT 采集目标（EXE-08）。
 *
 * <p>{@code password} 与 {@code privateKeyPath} 只在 {@code SystemSshClient} 组装进程参数时使用；
 * {@link #toString()} 是这类对象唯一允许进日志 / 证据的形态，因此绝不含口令，
 * 也不输出私钥完整路径（只标记是否配了私钥）。
 */
public record DutTarget(String host,
                        int port,
                        String user,
                        String password,
                        String privateKeyPath,
                        String jumpHost,
                        String jumpUser) {

    public static final int DEFAULT_PORT = 22;

    /** 只输出 {@code user@host[:port]} 与跳板信息，可用于日志与错误消息。 */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        text.append(user).append('@').append(host);
        if (port != DEFAULT_PORT) {
            text.append(':').append(port);
        }
        if (jumpHost != null && !jumpHost.isBlank()) {
            text.append(" via ");
            if (jumpUser != null && !jumpUser.isBlank()) {
                text.append(jumpUser).append('@');
            }
            text.append(jumpHost);
        }
        if (privateKeyPath != null && !privateKeyPath.isBlank()) {
            text.append(" (key:yes)");
        }
        return text.toString();
    }
}

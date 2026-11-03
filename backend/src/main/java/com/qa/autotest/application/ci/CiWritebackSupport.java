package com.qa.autotest.application.ci;

/**
 * 回写目标的判定与归一化（PLN-03）。
 *
 * <p>群晖 git server 只有 SSH，没有 HTTP/Web UI/webhook（ADR-0011 现状事实），
 * 因此任何 {@code ssh://} / {@code git@} 形式的仓库一律判定为「不支持状态回写」，
 * 不发起 HTTP 请求、不假装成功。
 */
public final class CiWritebackSupport {

    /** 群晖仓库：ssh 协议或显式群晖主机名。 */
    private static final String[] SYNOLOGY_MARKERS = {"ssh://", "git@", "synology", "syno."};

    private CiWritebackSupport() {
    }

    public static boolean isSynologyRepo(String repo) {
        if (repo == null || repo.isBlank()) {
            return false;
        }
        String lower = repo.trim().toLowerCase(java.util.Locale.ROOT);
        for (String marker : SYNOLOGY_MARKERS) {
            if (lower.startsWith(marker) || lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 归一化为 GitHub 的 {@code owner/repo}：
     * 接受 {@code owner/repo}、{@code https://github.com/owner/repo(.git)}。
     *
     * @return 合法时返回 owner/repo；无法识别 GitHub 仓库时返回 null
     */
    public static String normalizeGithubRepo(String repo) {
        if (repo == null || repo.isBlank()) {
            return null;
        }
        String value = repo.trim();
        String prefix = "https://github.com/";
        if (value.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
            value = value.substring(prefix.length());
        }
        if (value.endsWith(".git")) {
            value = value.substring(0, value.length() - 4);
        }
        if (!value.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            return null;
        }
        return value;
    }

    /** 触发响应里的回写预期：disabled 优先（根本不会尝试），其次 synology。 */
    public static String preview(String repo, CiProperties properties) {
        if (!properties.getWriteback().isEnabled()) {
            return "disabled";
        }
        return isSynologyRepo(repo) ? "not_supported" : "pending";
    }
}

package com.qa.autotest.domain.execution;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 失败簇键归一化（ORCH-03，v2.1）：把「同一类失败」的不同文本收敛到同一个键。
 *
 * <p>纯函数、**确定性**：同一输入永远得到同一输出，不做 k-means、不做 embedding
 * ——「两次划分逐字节一致」是 FR-v2.1-04 的硬判据，任何带随机或浮点累加的做法都过不了。
 *
 * <p>归一化顺序（顺序本身是语义，不能调换）：
 * <ol>
 *   <li>只取首行：多行堆栈的差异主要在后续帧，用整段文本会把同一失败拆成几十簇；</li>
 *   <li>绝对路径 → {@code <path>}：`/a/b/x.py` 与 `/c/d/y.py` 是同一处代码的不同副本；</li>
 *   <li>IP（含端口）→ {@code <ip>}：环境不同不该分成不同簇；</li>
 *   <li>数字 → {@code <n>}：`3 != 5` 与 `3 != 7` 是同一类断言失败；</li>
 *   <li>折叠空白、超长时截断为「33 字符前缀 + … + 4 位哈希」，总长 ≤ 40（见 {@link #shorten}）。</li>
 * </ol>
 *
 * <p>先替换路径再替换数字很重要：反过来会把路径里的 `v2.1` 之类先打成 {@code <n>}，
 * 让本来不同的路径看起来一样。
 */
public final class FailureClusterKey {

    /** 都为空时的固定簇名，宁可归堆也不丢数据。 */
    public static final String UNCLASSIFIED = "未分类";

    /** 簇键最大长度：与 metadata 单值上限同口径，便于在表格一列里显示。 */
    public static final int MAX_KEY_LENGTH = 40;

    /** 超长截断时保留的前缀长度；33 + 1（省略号）+ 4（哈希）= 38 ≤ {@link #MAX_KEY_LENGTH}。 */
    private static final int PREFIX_LENGTH = MAX_KEY_LENGTH - 7;

    private static final Pattern UNIX_PATH = Pattern.compile("(?:/[A-Za-z0-9_.-]+){2,}/?");
    private static final Pattern WINDOWS_PATH = Pattern.compile("[A-Za-z]:\\\\[^\\s]+");
    private static final Pattern IP_PORT = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private FailureClusterKey() {
    }

    /**
     * 由失败文本得到簇键。
     *
     * @param raw 用例级失败文本（{@code test_result.failure_msg}），允许为 null
     * @return 非空簇键；文本为空时返回 {@link #UNCLASSIFIED}
     */
    public static String of(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNCLASSIFIED;
        }
        String firstLine = raw.strip().lines().findFirst().orElse("").strip();
        if (firstLine.isEmpty()) {
            return UNCLASSIFIED;
        }
        String normalized = UNIX_PATH.matcher(firstLine).replaceAll("<path>");
        normalized = WINDOWS_PATH.matcher(normalized).replaceAll("<path>");
        normalized = IP_PORT.matcher(normalized).replaceAll("<ip>");
        normalized = NUMBER.matcher(normalized).replaceAll("<n>");
        normalized = SPACES.matcher(normalized).replaceAll(" ").strip();
        if (normalized.isEmpty()) {
            return UNCLASSIFIED;
        }
        if (normalized.length() <= MAX_KEY_LENGTH) {
            return normalized;
        }
        return shorten(normalized);
    }

    /**
     * 超长截断：保留前 {@value #PREFIX_LENGTH} 字符 + {@code …} + 4 位确定性哈希。
     *
     * <p>不能直接 {@code substring(0, 40)}：前缀相同的长失败文本（例如同一句断言后面
     * 跟着不同的实际值）会被截成同一个键，把不同的失败错误地并簇。加哈希后缀后，
     * 前 33 字符相同也能区分；总长 38，仍在 {@value #MAX_KEY_LENGTH} 以内。
     */
    private static String shorten(String normalized) {
        String hash = String.format("%08x", normalized.hashCode());
        return normalized.substring(0, PREFIX_LENGTH) + "…" + hash.substring(4);
    }

    /** 簇键的稳定比较：先按字符序，避免依赖 HashMap 迭代序。 */
    public static int compare(String left, String right) {
        return left.compareTo(right);
    }

    /** 展示用的小写类名（前端筛选不区分大小写时用）。 */
    public static String lower(FailureClass failureClass) {
        return failureClass == null ? "" : failureClass.name().toLowerCase(Locale.ROOT);
    }
}

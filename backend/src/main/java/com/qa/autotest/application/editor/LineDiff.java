package com.qa.autotest.application.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * 行级 diff（EDT-09 / FR-v0.3-21）：以 LCS 求最小编辑，标注相等 / 新增 / 删除。
 *
 * <p>纯函数、无外部依赖；超大文本（行数乘积超过 {@code MAX_CELLS}）退化为
 * 「整体删除 + 整体新增」，避免把内存吃光。
 */
public final class LineDiff {

    /** DP 单元格上限（2000 × 2000）。超过则退化为整文件替换。 */
    public static final long MAX_CELLS = 4_000_000L;

    public enum Op {
        EQUAL, ADD, REMOVE
    }

    /**
     * @param leftNo  旧版行号（REMOVE/EQUAL 有效，ADD 为 0）
     * @param rightNo 新版行号（ADD/EQUAL 有效，REMOVE 为 0）
     */
    public record DiffLine(Op op, int leftNo, int rightNo, String text) {
    }

    private LineDiff() {
    }

    public static List<DiffLine> between(String left, String right) {
        String[] a = lines(left);
        String[] b = lines(right);
        if ((long) a.length * b.length > MAX_CELLS) {
            return fallback(a, b);
        }
        int[][] lcs = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--) {
            for (int j = b.length - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<DiffLine> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < a.length && j < b.length) {
            if (a[i].equals(b[j])) {
                out.add(new DiffLine(Op.EQUAL, i + 1, j + 1, a[i]));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                out.add(new DiffLine(Op.REMOVE, i + 1, 0, a[i]));
                i++;
            } else {
                out.add(new DiffLine(Op.ADD, 0, j + 1, b[j]));
                j++;
            }
        }
        while (i < a.length) {
            out.add(new DiffLine(Op.REMOVE, i + 1, 0, a[i]));
            i++;
        }
        while (j < b.length) {
            out.add(new DiffLine(Op.ADD, 0, j + 1, b[j]));
            j++;
        }
        return out;
    }

    private static List<DiffLine> fallback(String[] a, String[] b) {
        List<DiffLine> out = new ArrayList<>(a.length + b.length);
        for (int i = 0; i < a.length; i++) {
            out.add(new DiffLine(Op.REMOVE, i + 1, 0, a[i]));
        }
        for (int j = 0; j < b.length; j++) {
            out.add(new DiffLine(Op.ADD, 0, j + 1, b[j]));
        }
        return out;
    }

    /** 按行切分，统一换行符，去掉尾随换行产生的空尾行。 */
    static String[] lines(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] raw = normalized.split("\n", -1);
        if (raw.length > 0 && raw[raw.length - 1].isEmpty()) {
            String[] trimmed = new String[raw.length - 1];
            System.arraycopy(raw, 0, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return raw;
    }
}

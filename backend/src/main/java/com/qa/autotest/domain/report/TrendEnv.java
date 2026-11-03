package com.qa.autotest.domain.report;

/**
 * 趋势的环境口径（RPT-04 / FR-v0.5-20）。
 *
 * <p>环境 = DUT + LINK。同一用例换环境后趋势必须断开，不得把两个环境的耗时连成一条线，
 * 因此每条趋势点都带 {@code envKey}，前端按 envKey 分组画线。
 */
public final class TrendEnv {

    /** 空值占位符：让「DUT 为空」与「DUT 有值」也能稳定比较。 */
    public static final String EMPTY = "（空）";

    private TrendEnv() {
    }

    /** 稳定环境键，例如 {@code DUT=GPU-01;LINK=mlx5_0}。 */
    public static String envKey(String dut, String link) {
        return "DUT=" + display(dut) + ";LINK=" + display(link);
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? EMPTY : value;
    }
}

package com.qa.autotest.domain.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * metadata 十项门禁（RPT-07）。
 *
 * <p>判定口径（DEV-PLAN §3）：
 * <ul>
 *   <li>null / 空串 / 全空白 一律视为缺失；</li>
 *   <li>值长度超过 {@link #MAX_VALUE_LENGTH} 视为超长（RF 会在每个 test 行重复渲染，撑爆报告）；</li>
 *   <li>键名必须全大写精确匹配，小写同名键不算命中。</li>
 * </ul>
 *
 * <p>本类为纯领域对象，不依赖任何框架；后续 RPT-05 导出复用同一个 {@link #check(Map)}。
 */
public final class MetadataGate {

    /** 十项必填键，顺序即缺失列表的输出顺序（保证报告与 CSV 稳定）。 */
    public static final List<String> REQUIRED = List.of(
            "CASE_ID", "RUN_ID", "QA_PROJECT", "DUT", "LINK", "SW_BUILD",
            "BASELINE", "OPERATOR", "STARTED_AT", "CASE_KIND");

    /** 单值长度上限（字符）。 */
    public static final int MAX_VALUE_LENGTH = 40;

    private MetadataGate() {
    }

    /**
     * 校验 metadata。
     *
     * @param metadata 解析得到的键值表，允许为 null
     * @return 门禁结果；missing / tooLong 均按 {@link #REQUIRED} 顺序排列
     */
    public static GateResult check(Map<String, String> metadata) {
        Map<String, String> source = metadata == null ? Map.of() : metadata;
        List<String> missing = new ArrayList<>();
        List<String> tooLong = new ArrayList<>();
        for (String key : REQUIRED) {
            String value = source.get(key);
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
            if (value != null && value.length() > MAX_VALUE_LENGTH) {
                tooLong.add(key);
            }
        }
        return new GateResult(missing.isEmpty() && tooLong.isEmpty(),
                List.copyOf(missing), List.copyOf(tooLong));
    }
}

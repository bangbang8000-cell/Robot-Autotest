package com.qa.autotest.domain.ai;

/** AI 动作结果（AIX-04 / AIX-05）。DEGRADED 是正常降级态，不是失败。 */
public record AiActionResult(String action, String level, boolean degraded,
                             String summary, boolean audited) {
}

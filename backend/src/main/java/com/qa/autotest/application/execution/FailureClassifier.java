package com.qa.autotest.application.execution;

import com.qa.autotest.domain.execution.FailureClass;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 失败分类器：只吃文本与产物结论，不依赖文件系统（spec §4.7）。
 *
 * <p>优先级固定 CANCELED &gt; ENV &gt; INFRA &gt; ASSERT：
 * <ol>
 *   <li>带取消标记，或 reason 提示含 canceled / cancelled / timeout → CANCELED；
 *   （{@code timeout} 指平台取消或超时；{@code timed out} 属于 ENV，不会被这条吃掉）</li>
 *   <li>合并文本命中 ENV 规则 → ENV；</li>
 *   <li>产物不可用，或命中 INFRA 规则 → INFRA；</li>
 *   <li>其余 → ASSERT。</li>
 * </ol>
 */
public final class FailureClassifier {

    /** 平台侧取消/超时的 reason 提示词。 */
    private static final List<String> CANCEL_HINTS = List.of("canceled", "cancelled", "cancel", "timeout");

    private final FailureRuleSet rules;

    public FailureClassifier(FailureRuleSet rules) {
        this.rules = rules == null ? FailureRuleSet.defaults() : rules;
    }

    public FailureClass classify(FailureInput input) {
        if (input == null) {
            return FailureClass.ASSERT;
        }
        if (input.canceled() || hasCancelHint(input.reasonHint())) {
            return FailureClass.CANCELED;
        }
        Optional<FailureClass> matched = rules.match(merged(input));
        if (matched.isPresent() && matched.get() == FailureClass.CANCELED) {
            return FailureClass.CANCELED;
        }
        if (matched.isPresent() && matched.get() == FailureClass.ENV) {
            return FailureClass.ENV;
        }
        if (!input.artifactOk()) {
            return FailureClass.INFRA;
        }
        if (matched.isPresent() && matched.get() == FailureClass.INFRA) {
            return FailureClass.INFRA;
        }
        return FailureClass.ASSERT;
    }

    /** 所有可供分类的文本，按诊断顺序拼接：原因 → stdout → stderr → pabot 管理器日志 → robot stderr。 */
    private static String merged(FailureInput input) {
        StringBuilder builder = new StringBuilder();
        append(builder, input.reasonHint());
        append(builder, input.stdout());
        append(builder, input.stderr());
        append(builder, input.pabotManagerLog());
        append(builder, input.robotStderr());
        return builder.toString();
    }

    private static void append(StringBuilder builder, String part) {
        if (part != null && !part.isEmpty()) {
            builder.append(part).append('\n');
        }
    }

    private static boolean hasCancelHint(String reasonHint) {
        if (reasonHint == null || reasonHint.isBlank()) {
            return false;
        }
        String lower = reasonHint.toLowerCase(Locale.ROOT);
        return CANCEL_HINTS.stream().anyMatch(lower::contains);
    }

    /** 分类输入：取消标记 + 原因提示 + 四份文本 + 产物是否可用。 */
    public record FailureInput(boolean canceled, String reasonHint, String stdout, String stderr,
                               String pabotManagerLog, String robotStderr, boolean artifactOk) {
    }
}

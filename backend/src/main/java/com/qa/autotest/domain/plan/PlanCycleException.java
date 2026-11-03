package com.qa.autotest.domain.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * 计划依赖成环（PLN-02）。异常里必须带环路径（A → B → C → A），
 * 只报「存在循环依赖」等于让人自己找。
 *
 * <p>纯领域异常，应用层负责翻译成 HTTP 400 + 中文提示。
 */
public class PlanCycleException extends RuntimeException {

    private final List<Long> cyclePath;

    public PlanCycleException(List<Long> cyclePath) {
        super("检测到循环依赖: " + format(cyclePath));
        this.cyclePath = List.copyOf(cyclePath);
    }

    public List<Long> cyclePath() {
        return cyclePath;
    }

    static String format(List<Long> path) {
        StringBuilder text = new StringBuilder();
        for (Long id : path) {
            if (text.length() > 0) {
                text.append(" → ");
            }
            text.append(id);
        }
        return text.toString();
    }

    static List<Long> cycle(List<Long> path, Long repeated) {
        List<Long> output = new ArrayList<>(path);
        output.add(repeated);
        return output;
    }
}

package com.qa.autotest.application.execution;

import com.qa.autotest.domain.execution.FailureClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXE-06 失败分类：30 条样本（TC-v0.4-601~606）逐条断言分类与重试判定。
 *
 * <p>样本真源 {@code fixtures/failure-class/30-samples.tsv}；每条样本运行时打印
 * {@code sample id=... expected=... actual=... retry=... result=...}，
 * 证据文件 {@code docs/evidence/v0.4/failure-class-30.md} 的「实际」列取自这些输出行。
 */
class FailureClassifierTest {

    private static final String HEADER = "id\texpected\tretry\tcanceled\tartifactOk\t"
            + "reasonHint\tstdout\tstderr\tpabotManagerLog\trobotStderr";

    private static final String SAMPLES = "fixtures/failure-class/30-samples.tsv";

    private final FailureClassifier classifier = new FailureClassifier(FailureRuleSet.defaults());

    private final RetryPolicy retryPolicy = new RetryPolicy();

    @Test
    void 三十条样本分类与重试判定全部符合期望() {
        List<Sample> samples = load();
        assertEquals(30, samples.size(), "样本数必须恰为 30 条");

        int passed = 0;
        for (Sample sample : samples) {
            FailureClass actual = classifier.classify(sample.input());
            boolean retry = retryPolicy.shouldRetry(actual, 0, 1);
            boolean ok = actual == sample.expected() && retry == sample.retry();
            System.out.printf("sample id=%s expected=%s actual=%s retry=%s result=%s%n",
                    sample.id(), sample.expected(), actual, retry ? "yes" : "no",
                    ok ? "PASS" : "FAIL");
            assertEquals(sample.expected(), actual, "样本 " + sample.id() + " 分类不符");
            assertEquals(sample.retry(), retry, "样本 " + sample.id() + " 重试判定不符");
            passed++;
        }
        assertEquals(30, passed, "30 条样本必须全部通过");
        assertCoverage(samples);
    }

    @Test
    void 断言失败绝不重试() {
        FailureClass cls = classifier.classify(new FailureClassifier.FailureInput(
                false, null,
                "FAIL: Expected 200 Gbps but got 188 Gbps", null, null, null, true));
        assertEquals(FailureClass.ASSERT, cls);
        assertFalse(retryPolicy.shouldRetry(cls, 0, 1), "断言失败不得重试");
        assertFalse(retryPolicy.shouldRetry(cls, 0, 5), "断言失败在任何次数下都不得重试");
    }

    @Test
    void 环境失败重试一次后不再重试() {
        FailureClass cls = classifier.classify(new FailureClassifier.FailureInput(
                false, null, null,
                "ssh: connect to host 10.202.54.106 port 22: Connection refused", null, null, true));
        assertEquals(FailureClass.ENV, cls);
        assertTrue(retryPolicy.shouldRetry(cls, 0, 1), "环境失败首次允许重试");
        assertFalse(retryPolicy.shouldRetry(cls, 1, 1), "环境失败重试一次后不得再重试");
    }

    @Test
    void 取消优先级高于环境错误() {
        FailureClass cls = classifier.classify(new FailureClassifier.FailureInput(
                true, null, "ssh: connect to host 10.202.54.106 port 22: Connection refused",
                null, null, null, true));
        assertEquals(FailureClass.CANCELED, cls);
    }

    /** 四类覆盖下限：ENV ≥ 8、INFRA ≥ 8、ASSERT ≥ 8、CANCELED ≥ 4。 */
    private static void assertCoverage(List<Sample> samples) {
        long env = samples.stream().filter(s -> s.expected() == FailureClass.ENV).count();
        long infra = samples.stream().filter(s -> s.expected() == FailureClass.INFRA).count();
        long assertion = samples.stream().filter(s -> s.expected() == FailureClass.ASSERT).count();
        long canceled = samples.stream().filter(s -> s.expected() == FailureClass.CANCELED).count();
        assertTrue(env >= 8, "ENV 样本不足 8 条，实际 " + env);
        assertTrue(infra >= 8, "INFRA 样本不足 8 条，实际 " + infra);
        assertTrue(assertion >= 8, "ASSERT 样本不足 8 条，实际 " + assertion);
        assertTrue(canceled >= 4, "CANCELED 样本不足 4 条，实际 " + canceled);
    }

    /** 解析 TSV：第一行为表头，其余每行一条样本，字段数必须为 10。 */
    private List<Sample> load() {
        List<String> lines;
        try {
            lines = Files.readAllLines(resource(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取样本文件失败: " + SAMPLES, ex);
        }
        if (lines.isEmpty() || !HEADER.equals(lines.get(0))) {
            throw new IllegalStateException("样本文件表头不符合约定");
        }
        List<Sample> samples = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] cells = line.split("\t", -1);
            if (cells.length != 10) {
                throw new IllegalStateException("样本 " + cells[0] + " 列数不为 10");
            }
            FailureClass expected = parseExpected(cells[1]);
            boolean retry = parseFlag(cells[2], "retry");
            boolean canceled = parseFlag(cells[3], "canceled");
            boolean artifactOk = parseFlag(cells[4], "artifactOk");
            samples.add(new Sample(cells[0], expected, retry,
                    new FailureClassifier.FailureInput(canceled, blankToNull(cells[5]),
                            blankToNull(cells[6]), blankToNull(cells[7]),
                            blankToNull(cells[8]), blankToNull(cells[9]), artifactOk)));
        }
        return samples;
    }

    private Path resource() {
        try {
            var url = getClass().getClassLoader().getResource(SAMPLES);
            if (url == null) {
                throw new IllegalStateException("找不到样本文件: " + SAMPLES);
            }
            return Path.of(url.toURI());
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("样本文件路径非法: " + SAMPLES, ex);
        }
    }

    private static FailureClass parseExpected(String cell) {
        String value = cell.trim();
        if (!value.equals(value.toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalStateException("expected 列必须大写: " + value);
        }
        return FailureClass.valueOf(value);
    }

    private static boolean parseFlag(String cell, String column) {
        String value = cell.trim();
        if ("yes".equals(value)) {
            return true;
        }
        if ("no".equals(value)) {
            return false;
        }
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalStateException(column + " 列取值非法: " + value);
    }

    private static String blankToNull(String cell) {
        return cell.isBlank() ? null : cell;
    }

    private record Sample(String id, FailureClass expected, boolean retry,
                          FailureClassifier.FailureInput input) {
    }
}

package com.qa.autotest.application.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("N15 运行进度：控制台行解析")
class RunProgressRegistryTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-07T02:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("逐条结果行进计数：worker 前缀被剥掉，PASS/FAIL/SKIP 分开统计")
    void 结果行计数() {
        RunProgressRegistry registry = new RunProgressRegistry(CLOCK);
        registry.begin("RUN-1");
        registry.onLine("RUN-1", "[worker:0] 登录成功                                                    | PASS |");
        registry.onLine("RUN-1", "[worker:1] 鉴权失败                                                    | FAIL |");
        registry.onLine("RUN-1", "[stdout] 跳过示例                                                       | SKIP |");

        RunProgressRegistry.Progress progress = registry.snapshot("RUN-1");
        assertThat(progress).isNotNull();
        assertThat(progress.completed()).isEqualTo(3);
        assertThat(progress.passed()).isEqualTo(1);
        assertThat(progress.failed()).isEqualTo(1);
        assertThat(progress.skipped()).isEqualTo(1);
        assertThat(progress.lastTest()).isEqualTo("跳过示例");
        assertThat(progress.lastVerdict()).isEqualTo("SKIP");
    }

    @Test
    @DisplayName("RF 汇总行给出总数：N tests, X passed, Y failed")
    void 汇总行给出总数() {
        RunProgressRegistry registry = new RunProgressRegistry(CLOCK);
        registry.begin("RUN-2");
        registry.onLine("RUN-2", "[worker:0] 12 tests, 10 passed, 2 failed.");

        RunProgressRegistry.Progress progress = registry.snapshot("RUN-2");
        assertThat(progress.total()).isEqualTo(12);
        assertThat(progress.passed()).isEqualTo(10);
        assertThat(progress.failed()).isEqualTo(2);
        // 逐条结果行未经过，completed 保持 0：不伪造进度
        assertThat(progress.completed()).isZero();
    }

    @Test
    @DisplayName("套件汇总行不计入用例数：子套件与根套件都排除（实测 2 用例曾报 3 倍计数）")
    void 套件行被排除() {
        RunProgressRegistry registry = new RunProgressRegistry(CLOCK);
        registry.begin("RUN-20261107173255-5f0000");
        // 下面三行是 pabot worker 控制台原文（pabot_results/0/robot_stdout.out 第 8、6、11 行）
        registry.onLine("RUN-20261107173255-5f0000",
                "[worker:0] run-RUN-20261107173255-5f0000.Demo :: NODX-03 端到端示例（本机合成... | PASS |");
        registry.onLine("RUN-20261107173255-5f0000",
                "[worker:0] NODX03 示例通过 :: 节点真实执行的最小 RF 用例                    | PASS |");
        registry.onLine("RUN-20261107173255-5f0000",
                "[worker:0] run-RUN-20261107173255-5f0000                                | PASS |");

        RunProgressRegistry.Progress progress = registry.snapshot("RUN-20261107173255-5f0000");
        assertThat(progress.completed()).isEqualTo(1);
        assertThat(progress.passed()).isEqualTo(1);
        assertThat(progress.lastTest()).isEqualTo("NODX03 示例通过");
    }

    @Test
    @DisplayName("结果后缀被 Log To Console 挤到独立行时，沿用前面名称行的用例名")
    void 空名结果行沿用名称行() {
        RunProgressRegistry registry = new RunProgressRegistry(CLOCK);
        registry.begin("RUN-5");
        // pabot_results/1/robot_stdout.out 第 6~9 行原文
        registry.onLine("RUN-5", "[worker:1] A5 循环与分支 :: FOR 内逐轮输出 + IF 分支，用于核对关键字树完整性     A5-FOR-0");
        registry.onLine("RUN-5", "[worker:1] A5-FOR-1");
        registry.onLine("RUN-5", "[worker:1] A5-FOR-2");
        registry.onLine("RUN-5", "[worker:1] | PASS |");

        RunProgressRegistry.Progress progress = registry.snapshot("RUN-5");
        assertThat(progress.completed()).isEqualTo(1);
        assertThat(progress.lastTest()).isEqualTo("A5 循环与分支");
    }

    @Test
    @DisplayName("用例名与首条日志挤在同一行时，取对齐填充前的名称段（B2 一万行日志实测格式）")
    void 名称与首条日志同行() {
        RunProgressRegistry registry = new RunProgressRegistry(CLOCK);
        registry.begin("RUN-7");
        // pabot_results/0/robot_stdout.out 第 4 行原文：名称 + 填充 + 第一条 Log To Console
        registry.onLine("RUN-7", "[worker:0] B2 输出一万行                                                         第 0 行 B2 log line payload");
        registry.onLine("RUN-7", "[worker:0] 第 1 行 B2 log line payload");
        registry.onLine("RUN-7", "[worker:0] | PASS |");

        RunProgressRegistry.Progress progress = registry.snapshot("RUN-7");
        assertThat(progress.completed()).isEqualTo(1);
        // 普通日志行没有对齐填充，不得覆盖兜底名
        assertThat(progress.lastTest()).isEqualTo("B2 输出一万行");
    }

    @Test
    @DisplayName("分片子套件汇总先到：total 不落到已完成数以下")
    void 汇总总数不低于已完成数() {
        RunProgressRegistry registry = new RunProgressRegistry(CLOCK);
        registry.begin("RUN-6");
        registry.onLine("RUN-6", "[worker:0] 用例甲                                                    | PASS |");
        registry.onLine("RUN-6", "[worker:1] 用例乙                                                    | PASS |");
        registry.onLine("RUN-6", "[worker:0] 1 test, 1 passed, 0 failed");

        RunProgressRegistry.Progress progress = registry.snapshot("RUN-6");
        assertThat(progress.completed()).isEqualTo(2);
        assertThat(progress.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("未跟踪的 run 返回 null；阶段可改，耗时按注入时钟计算")
    void 未跟踪与阶段() {
        RunProgressRegistry registry = new RunProgressRegistry(CLOCK);
        assertThat(registry.snapshot("RUN-404")).isNull();

        registry.begin("RUN-3");
        registry.phase("RUN-3", "reporting");
        RunProgressRegistry.Progress progress = registry.snapshot("RUN-3");
        assertThat(progress.phase()).isEqualTo("reporting");
        assertThat(progress.elapsedSeconds()).isZero();
    }

    @Test
    @DisplayName("非结果行与非汇总行不参与计数")
    void 无关行被忽略() {
        RunProgressRegistry registry = new RunProgressRegistry(CLOCK);
        registry.begin("RUN-4");
        registry.onLine("RUN-4", "[system] 开始执行 pabot: pabot ...");
        registry.onLine("RUN-4", "==============================================================================");

        RunProgressRegistry.Progress progress = registry.snapshot("RUN-4");
        assertThat(progress.completed()).isZero();
        assertThat(progress.total()).isZero();
    }
}

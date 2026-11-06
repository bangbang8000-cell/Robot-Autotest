package com.qa.autotest.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 实时日志推送的调度开关（EXE-04）。
 *
 * <p><b>为什么单独一个类：</b>{@code RunLogService.flush()} 挂着
 * {@code @Scheduled(fixedRate = 100)}，但 {@code @Scheduled} 只在本上下文里
 * 「有人 {@code @EnableScheduling}」时才生效。原先只有
 * {@code ExecutionSchedulerConfig}（{@code execution.scheduling.enabled=true}）、
 * {@code PlanScheduler}（{@code planning.scheduler-enabled=true}）、
 * {@code RetentionScheduler} 三个带条件的类开启调度 —— 于是出现一个真实缺陷：
 * <b>执行调度一旦关闭（只读观察部署、测试 profile、或刚起步未开调度），
 * WebSocket 就连上不推日志</b>；补发的历史 200 行正常，实时增量却永远空着，
 * 现场表现是「日志面板像卡住了」。
 *
 * <p>日志推送是通道的基础能力，不该跟「执行调度是否开启」绑在一起，因此在这里
 * 无条件开启调度。重复 {@code @EnableScheduling} 在 Spring 里是幂等的。
 */
@Configuration
@EnableScheduling
public class RunLogFlushConfig {
}

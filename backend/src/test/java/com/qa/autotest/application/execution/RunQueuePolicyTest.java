package com.qa.autotest.application.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

/** 队列挑选策略单测（FR-v0.4-03/05）。 */
class RunQueuePolicyTest {

    private static RunRecord run(long id, long projectId, int priority, Instant enqueuedAt) {
        return new RunRecord(id, "RUN-" + id, projectId, null, RunState.QUEUED, priority, 1,
                0, 0, 0, 0, null, null, null, null, enqueuedAt, null, null);
    }

    @Test
    @DisplayName("优先级降序、入队时间升序")
    void 排序规则() {
        Instant base = Instant.parse("2026-11-03T00:00:00Z");
        RunQueuePolicy policy = new RunQueuePolicy(4);
        List<RunRecord> pending = List.of(
                run(3, 1, 0, base.plusSeconds(2)),
                run(2, 1, 5, base.plusSeconds(1)),
                run(1, 1, 5, base));

        List<RunRecord> picked = policy.pick(pending, 0);
        assertEquals(List.of(1L, 2L, 3L), picked.stream().map(RunRecord::id).toList());
    }

    @Test
    @DisplayName("并发已满时不再派发")
    void 并发上限() {
        RunQueuePolicy policy = new RunQueuePolicy(2);
        List<RunRecord> pending = List.of(run(1, 1, 0, Instant.now()));
        assertTrue(policy.pick(pending, 2).isEmpty());
    }

    @Test
    @DisplayName("跨项目轮转：新项目不被老项目积压饿死")
    void 跨项目轮转() {
        Instant base = Instant.parse("2026-11-03T00:00:00Z");
        RunQueuePolicy policy = new RunQueuePolicy(2);
        List<RunRecord> pending = List.of(
                run(1, 1, 9, base),
                run(2, 1, 9, base.plusSeconds(1)),
                run(3, 1, 9, base.plusSeconds(2)),
                run(4, 2, 0, base.plusSeconds(3)));

        List<RunRecord> picked = policy.pick(pending, 0);
        assertEquals(2, picked.size());
        assertEquals(1L, picked.stream().filter(r -> r.projectId() == 2L).count());
        assertEquals(1L, picked.stream().filter(r -> r.projectId() == 1L).count());
    }
}

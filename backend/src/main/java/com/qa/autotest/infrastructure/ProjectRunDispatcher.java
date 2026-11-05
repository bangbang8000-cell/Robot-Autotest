package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.execution.RunDispatcher;
import com.qa.autotest.common.tenant.TenantContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按项目分道的运行派发器（EXEC-10，收口 FR-v0.4-03 / 05）。
 *
 * <p>每个项目一条独立队列 + 独立线程池，池大小 = {@code execution.max-parallel}（默认 4）：
 * <ul>
 *   <li>同一项目内按 {@code priority} 降序，再按提交先后（等价于创建先后）排队，达上限只排队不拒绝；</li>
 *   <li>不同项目分属不同队列与线程池，互不阻塞；</li>
 *   <li>同一 {@code runId} 在途去重：重复提交直接丢弃并告警。</li>
 * </ul>
 *
 * <p>提交时用 {@link TenantContext#runAs} 包裹，保证异步线程里租户守卫与审计拿得到 project_id。
 */
@Component
public class ProjectRunDispatcher implements RunDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRunDispatcher.class);
    private static final long IDLE_KEEP_ALIVE_SECONDS = 60L;

    private final Map<Long, ProjectLane> lanes = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicLong sequence = new AtomicLong();
    private final int maxParallel;

    public ProjectRunDispatcher(ExecutionProperties props) {
        this.maxParallel = Math.max(1, props.getMaxParallel());
    }

    @Override
    public void submit(long projectId, Long planId, String runId, int priority, Runnable task) {
        if (!inFlight.add(runId)) {
            LOG.warn("run {} 已在派发队列中，忽略重复提交（plan={}）", runId, planId);
            return;
        }
        Runnable item = new RunTask(projectId, planId, runId, priority,
                sequence.incrementAndGet(), task);
        try {
            lane(projectId).executor.execute(item);
        } catch (RuntimeException rejected) {
            inFlight.remove(runId);
            throw rejected;
        }
    }

    private ProjectLane lane(long projectId) {
        return lanes.computeIfAbsent(projectId, this::newLane);
    }

    private ProjectLane newLane(long projectId) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                maxParallel, maxParallel, IDLE_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(),
                runnable -> new Thread(runnable, "run-dispatch-p" + projectId));
        executor.allowCoreThreadTimeOut(true);
        return new ProjectLane(executor);
    }

    /** 当前在途 run 数（单测断言用）。 */
    int inFlightCount() {
        return inFlight.size();
    }

    /** 当前已建道的项目数（单测断言用）。 */
    int laneCount() {
        return lanes.size();
    }

    @PreDestroy
    public void shutdown() {
        for (ProjectLane lane : lanes.values()) {
            lane.executor.shutdownNow();
        }
        lanes.clear();
    }

    private record ProjectLane(ThreadPoolExecutor executor) {
    }

    /** 队列元素：优先级高的先出队；同优先级按提交序号（即创建先后）先进先出。 */
    private final class RunTask implements Runnable, Comparable<RunTask> {
        private final long projectId;
        private final Long planId;
        private final String runId;
        private final int priority;
        private final long seq;
        private final Runnable task;

        private RunTask(long projectId, Long planId, String runId, int priority,
                        long seq, Runnable task) {
            this.projectId = projectId;
            this.planId = planId;
            this.runId = runId;
            this.priority = priority;
            this.seq = seq;
            this.task = task;
        }

        @Override
        public void run() {
            TenantContext.runAs(null, "runner", projectId, () -> {
                try {
                    task.run();
                } catch (RuntimeException ex) {
                    LOG.error("运行 {} 执行派发异常（plan={}）: {}", runId, planId, ex.getMessage(), ex);
                } finally {
                    inFlight.remove(runId);
                }
            });
        }

        @Override
        public int compareTo(RunTask other) {
            int byPriority = Integer.compare(other.priority, this.priority);
            return byPriority != 0 ? byPriority : Long.compare(this.seq, other.seq);
        }
    }
}

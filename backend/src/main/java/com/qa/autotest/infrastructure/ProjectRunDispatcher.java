package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.execution.RunDispatcher;
import com.qa.autotest.application.execution.RunQueueRepository;
import com.qa.autotest.common.tenant.TenantContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按项目分道的运行派发器（EXEC-10，收口 FR-v0.4-03 / 05；NODX-02 加节点道）。
 *
 * <p>每个项目一条独立队列 + 独立线程池，池大小 = {@code execution.max-parallel}（默认 4）：
 * <ul>
 *   <li>同一项目内按 {@code priority} 降序，再按提交先后（等价于创建先后）排队，达上限只排队不拒绝；</li>
 *   <li>不同项目分属不同队列与线程池，互不阻塞；</li>
 *   <li>同一 {@code runId} 在途去重：重复提交直接丢弃并告警。</li>
 * </ul>
 *
 * <p>NODX-02：指定执行节点的 run 走 {@link #submitToNode}，按<b>节点</b>独立建道，
 * 池大小 = 节点 {@code max_concurrency}，与本机项目道互不占用（并发独立计数）；
 * 本机路径 {@link #submit} 的行为与 v2.0 完全一致，只是内部道键从裸项目 id
 * 换成 {@code p<projectId>} 字符串（等价重构，语义不变）。
 *
 * <p>提交时用 {@link TenantContext#runAs} 包裹，保证异步线程里租户守卫与审计拿得到 project_id。
 */
@Component
public class ProjectRunDispatcher implements RunDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRunDispatcher.class);
    private static final long IDLE_KEEP_ALIVE_SECONDS = 60L;

    /** 道键：本机 {@code p<projectId>}，节点 {@code n<nodeId>}。 */
    private final Map<String, ProjectLane> lanes = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    /** runId → 所属项目：查询位次时不必让调用方再传一次 projectId，也避免跨项目串道。 */
    private final Map<String, Long> projectOfRun = new ConcurrentHashMap<>();
    /** runId → 道键：节点 run 与本机 run 各查各的道（位次、占用都按道算）。 */
    private final Map<String, String> laneOfRun = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final int maxParallel;
    private final RunQueueRepository queues;

    public ProjectRunDispatcher(ExecutionProperties props, RunQueueRepository queues) {
        this.maxParallel = Math.max(1, props.getMaxParallel());
        this.queues = queues;
    }

    @Override
    public void submit(long projectId, Long planId, String runId, int priority, Runnable task) {
        submitToLane(laneKeyOfProject(projectId), maxParallel, projectId, planId, runId, priority, task);
    }

    @Override
    public void submitToNode(long projectId, long nodeId, int nodeConcurrency, Long planId,
                             String runId, int priority, Runnable task) {
        submitToLane(laneKey(nodeId), Math.max(1, nodeConcurrency),
                projectId, planId, runId, priority, task);
    }

    private void submitToLane(String laneKey, int laneConcurrency, long projectId, Long planId,
                              String runId, int priority, Runnable task) {
        if (!inFlight.add(runId)) {
            LOG.warn("run {} 已在派发队列中，忽略重复提交（plan={}）", runId, planId);
            return;
        }
        projectOfRun.put(runId, projectId);
        laneOfRun.put(runId, laneKey);
        queues.enqueue(projectId, runId, priority);
        Runnable item = new RunTask(laneKey, projectId, planId, runId, priority,
                sequence.incrementAndGet(), task);
        try {
            lane(laneKey, laneConcurrency).executor.execute(item);
        } catch (RuntimeException rejected) {
            inFlight.remove(runId);
            projectOfRun.remove(runId);
            laneOfRun.remove(runId);
            throw rejected;
        }
    }

    /**
     * 队列位次（EXEC-10 残余）：位次按同项目队列中的 {@link RunTask#compareTo} 排序计算，
     * 与线程池真实出队顺序一致——优先级高的排前面，同优先级按提交先后。
     */
    @Override
    public Optional<QueueStatus> status(String runId) {
        Long projectId = projectOfRun.get(runId);
        String laneKey = laneOfRun.get(runId);
        if (projectId == null || laneKey == null) {
            return Optional.empty();
        }
        ProjectLane lane = lanes.get(laneKey);
        if (lane == null) {
            return Optional.empty();
        }
        Object[] waiting = lane.executor.getQueue().toArray();
        int running = lane.executor.getActiveCount();
        RunTask target = null;
        for (Object item : waiting) {
            if (item instanceof RunTask task && task.runId.equals(runId)) {
                target = task;
                break;
            }
        }
        if (target == null) {
            // 已占用线程（在途但不在队列里）；不在途则说明派发已结束，交给调用方按 DB 状态回答
            return inFlight.contains(runId)
                    ? Optional.of(new QueueStatus(runId, "running", 0, waiting.length, running,
                            lane.maxParallel))
                    : Optional.empty();
        }
        int ahead = 0;
        for (Object item : waiting) {
            if (item instanceof RunTask task && task.compareTo(target) < 0) {
                ahead++;
            }
        }
        return Optional.of(new QueueStatus(runId, "queued", ahead + 1,
                waiting.length, running, lane.maxParallel));
    }

    private static String laneKey(long id) {
        return "n" + id;
    }

    private static String laneKeyOfProject(long projectId) {
        return "p" + projectId;
    }

    private ProjectLane lane(String laneKey, int laneConcurrency) {
        return lanes.computeIfAbsent(laneKey, key -> newLane(key, laneConcurrency));
    }

    private ProjectLane newLane(String laneKey, int laneConcurrency) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                laneConcurrency, laneConcurrency, IDLE_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(),
                runnable -> new Thread(runnable, "run-dispatch-" + laneKey));
        executor.allowCoreThreadTimeOut(true);
        return new ProjectLane(executor, laneConcurrency);
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

    private record ProjectLane(ThreadPoolExecutor executor, int maxParallel) {
    }

    /** 队列元素：优先级高的先出队；同优先级按提交序号（即创建先后）先进先出。 */
    private final class RunTask implements Runnable, Comparable<RunTask> {
        private final String laneKey;
        private final long projectId;
        private final Long planId;
        private final String runId;
        private final int priority;
        private final long seq;
        private final Runnable task;

        private RunTask(String laneKey, long projectId, Long planId, String runId, int priority,
                        long seq, Runnable task) {
            this.laneKey = laneKey;
            this.projectId = projectId;
            this.planId = planId;
            this.runId = runId;
            this.priority = priority;
            this.seq = seq;
            this.task = task;
        }

        @Override
        public void run() {
            queues.markRunning(projectId, runId);
            TenantContext.runAs(null, "runner", projectId, () -> {
                try {
                    task.run();
                } catch (RuntimeException ex) {
                    LOG.error("运行 {} 执行派发异常（plan={}）: {}", runId, planId, ex.getMessage(), ex);
                } finally {
                    queues.markDone(projectId, runId);
                    inFlight.remove(runId);
                    projectOfRun.remove(runId);
                    laneOfRun.remove(runId);
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

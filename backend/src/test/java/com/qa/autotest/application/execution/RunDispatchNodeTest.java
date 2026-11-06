package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.application.repository.ExecutionNodeRepository;
import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.RunOrigin;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import com.qa.autotest.domain.node.ExecutionNode;
import com.qa.autotest.infrastructure.ProjectRunDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NODX-02 指定节点执行单测（正/负 + 并发独立）。
 *
 * <p>三条判据（DEV-PLAN §10.11）：
 * ① 离线/未上线/停用节点发起被拒且原因明确、不回落本机（run 不落库）；
 * ② ONLINE 节点发起成功：run.node_id 落库、走节点道；
 * ③ 节点并发按节点独立计数，与本机项目道互不占用。
 */
class RunDispatchNodeTest {

    private static final long PROJECT = ExecutionTestSupport.PROJECT;

    private InMemoryRunRepository repo;
    private NodeRepoStub nodeRepo;
    private RecordingDispatcher dispatcher;
    private RunService runs;
    private QuickRunService quickRuns;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L, "admin", PROJECT);
        repo = new InMemoryRunRepository();
        nodeRepo = new NodeRepoStub();
        dispatcher = new RecordingDispatcher();
        runs = new RunService(proxiedRepo(repo), ExecutionTestSupport.access(),
                ExecutionTestSupport.audit(), new RunIdGenerator(),
                ExecutionTestSupport.props(), ExecutionTestSupport.CLOCK,
                List.of(), nodeRepo);
        quickRuns = new QuickRunService(runs, null, dispatcher,
                ExecutionTestSupport.access(), ExecutionTestSupport.audit(),
                ExecutionTestSupport.props(), null, nodeRepo);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("负：OFFLINE 节点发起被拒、原因明确、不回落本机（run 不落库）")
    void 离线节点发起被拒() {
        nodeRepo.put(node(5, ExecutionNode.OFFLINE, Instant.EPOCH));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> runs.create(command(5L)));

        assertEquals(ErrorCode.CONFLICT, ex.errorCode());
        assertTrue(ex.getMessage().contains("OFFLINE"), ex.getMessage());
        assertTrue(ex.getMessage().contains("不回落本机"), ex.getMessage());
        assertEquals(0, repo.insertCount(), "拒绝发生在落库之前——离线不得留下任何 run");
        assertTrue(dispatcher.submits.isEmpty() && dispatcher.nodeSubmits.isEmpty(),
                "拒绝发生在派发之前");
    }

    @Test
    @DisplayName("负：NEW（未心跳）与 DISABLED（停用）各给各的原因")
    void 未上线与停用节点被拒() {
        nodeRepo.put(node(1, ExecutionNode.NEW, null));
        nodeRepo.put(node(9, ExecutionNode.DISABLED, Instant.now()));

        BusinessException newUser = assertThrows(BusinessException.class,
                () -> runs.create(command(1L)));
        assertTrue(newUser.getMessage().contains("NEW"), newUser.getMessage());

        BusinessException disabled = assertThrows(BusinessException.class,
                () -> runs.create(command(9L)));
        assertTrue(disabled.getMessage().contains("DISABLED"), disabled.getMessage());

        assertEquals(0, repo.insertCount());
    }

    @Test
    @DisplayName("负：节点不存在 → NOT_FOUND")
    void 节点不存在被拒() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> runs.create(command(404L)));
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode());
        assertEquals(0, repo.insertCount());
    }

    @Test
    @DisplayName("正：ONLINE 节点发起成功——run.node_id 落库、派发进节点道并带节点并发上限")
    void 在线节点发起成功() {
        nodeRepo.put(node(3, ExecutionNode.ONLINE, Instant.now()));

        RunRecord created = runs.create(command(3L));

        assertEquals(RunState.QUEUED, created.state());
        assertEquals(3L, assignedNodeId.get(created.runId()),
                "run.node_id 必须落库（NODX-02 判据）");
        assertTrue(assignedRunIdToProject.containsKey(created.runId()), "assignNode 带租户条件");

        quickRuns.dispatch(created, command(3L));

        assertEquals(1, dispatcher.nodeSubmits.size(), "指定节点只走节点道");
        NodeSubmit submit = dispatcher.nodeSubmits.get(0);
        assertEquals(3L, submit.nodeId);
        assertEquals(2, submit.nodeConcurrency, "节点并发上限 = max_concurrency");
        assertEquals(created.runId(), submit.runId);
        assertTrue(dispatcher.submits.isEmpty(), "指定节点的 run 不允许同时进本机道");
    }

    @Test
    @DisplayName("正：未指定节点走本机（v2.0 语义回归）")
    void 未指定走本机() {
        RunRecord created = runs.create(command(null));
        assertEquals(RunState.QUEUED, created.state());
        quickRuns.dispatch(created, command(null));
        assertEquals(1, dispatcher.submits.size());
        assertTrue(dispatcher.nodeSubmits.isEmpty(), "本机 run 不进节点道");
    }

    @Test
    @DisplayName("并发独立：本机道占满时节点道任务照常执行，反之亦然")
    void 并发独立计数() throws Exception {
        ExecutionProperties props = ExecutionTestSupport.props();
        props.setMaxParallel(1);
        ProjectRunDispatcher realDispatcher = new ProjectRunDispatcher(props, noopQueues());

        CountDownLatch localBlocked = new CountDownLatch(1);
        CountDownLatch localDone = new CountDownLatch(1);
        realDispatcher.submit(PROJECT, null, "RUN-LOCAL", 0, () -> {
            try {
                localBlocked.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            localDone.countDown();
        });

        // 节点道并发 2：两个任务都应立即执行，不被占满的本机道阻塞
        AtomicInteger nodeDone = new AtomicInteger();
        CountDownLatch nodeGate = new CountDownLatch(2);
        realDispatcher.submitToNode(PROJECT, 3, 2, null, "RUN-N1", 0, () -> {
            nodeDone.incrementAndGet();
            nodeGate.countDown();
        });
        realDispatcher.submitToNode(PROJECT, 3, 2, null, "RUN-N2", 0, () -> {
            nodeDone.incrementAndGet();
            nodeGate.countDown();
        });

        assertTrue(nodeGate.await(5, TimeUnit.SECONDS),
                "节点道任务必须与本机道并行（并发独立计数）");
        assertEquals(2, nodeDone.get());
        assertEquals(1, localDone.getCount(), "本机任务应仍占着自己的线程（节点任务没来抢道）");

        // 节点道占满（并发 1 的节点）：第 3 个节点任务排队；本机道空闲后任务照常执行
        CountDownLatch nodeBlocked = new CountDownLatch(1);
        realDispatcher.submitToNode(PROJECT, 4, 1, null, "RUN-N4A", 0, () -> {
            try {
                nodeBlocked.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        realDispatcher.submitToNode(PROJECT, 4, 1, null, "RUN-N4B", 0, () -> { });
        localBlocked.countDown();
        assertTrue(localDone.await(5, TimeUnit.SECONDS), "本机任务在节点占用时照常完成");

        realDispatcher.shutdown();
    }

    // ---- 基建 ----

    private static RunService.CreateRunCommand command(Long nodeId) {
        return new RunService.CreateRunCommand(PROJECT, null,
                List.of("suites/a.robot"), 1, null, List.of(),
                "req-node-" + nodeId, 0, 900, null, null, 0, nodeId);
    }

    private static ExecutionNode node(long id, String status, Instant heartbeat) {
        return new ExecutionNode(id, "node-" + id, "hash-" + id, status,
                true, false, 2, heartbeat, Instant.EPOCH, null);
    }

    private final Map<String, Long> assignedNodeId = new java.util.HashMap<>();
    private final Map<String, Long> assignedRunIdToProject = new java.util.HashMap<>();

    /** 记录 assignNode 的写库参数，其余委托内存仓储（InMemory 未建模 node_id）。 */
    private RunRepository proxiedRepo(InMemoryRunRepository delegate) {
        return (RunRepository) Proxy.newProxyInstance(
                RunRepository.class.getClassLoader(), new Class<?>[]{RunRepository.class},
                (proxy, method, args) -> {
                    if ("assignNode".equals(method.getName())) {
                        assignedNodeId.put((String) args[1], (Long) args[2]);
                        assignedRunIdToProject.put((String) args[1], (Long) args[0]);
                        return null;
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (java.lang.reflect.InvocationTargetException ex) {
                        throw ex.getTargetException();
                    }
                });
    }

    private static RunQueueRepository noopQueues() {
        return (RunQueueRepository) Proxy.newProxyInstance(
                RunQueueRepository.class.getClassLoader(),
                new Class<?>[]{RunQueueRepository.class},
                (proxy, method, args) -> null);
    }

    /** 节点仓储桩：内存 map，find/list 按 map 返回。 */
    private static final class NodeRepoStub implements ExecutionNodeRepository {
        private final Map<Long, ExecutionNode> nodes = new java.util.concurrent.ConcurrentHashMap<>();

        void put(ExecutionNode node) {
            nodes.put(node.id(), node);
        }

        @Override
        public Optional<ExecutionNode> find(long id) {
            return Optional.ofNullable(nodes.get(id));
        }

        @Override
        public Optional<ExecutionNode> findByName(String name) {
            return nodes.values().stream()
                    .filter(node -> node.name().equals(name)).findFirst();
        }

        @Override
        public boolean nameExists(String name) {
            return findByName(name).isPresent();
        }

        @Override
        public List<ExecutionNode> list() {
            return List.copyOf(nodes.values());
        }

        @Override
        public ExecutionNode insert(String name, String tokenHash, boolean rfAvailable,
                                    boolean pytestAvailable, int maxConcurrency, Long operatorId,
                                    String agentBaseUrl) {
            throw new UnsupportedOperationException("桩不支持注册");
        }

        @Override
        public void heartbeat(long id, boolean rfAvailable, boolean pytestAvailable) {
            throw new UnsupportedOperationException("桩不支持心跳");
        }

        @Override
        public int markStaleOffline(Instant staleBefore) {
            return 0;
        }

        @Override
        public void updateStatus(long id, String status, Long operatorId) {
            throw new UnsupportedOperationException("桩不支持状态变更");
        }

        @Override
        public Map<Long, Integer> runningRunsByNode() {
            return Map.of();
        }

        @Override
        public List<Long> findStaleOnlineIds(Instant staleBefore) {
            return List.of();
        }

        @Override
        public int failRunningRunsByNodes(List<Long> nodeIds, String reason) {
            return 0;
        }

        @Override
        public java.util.Map<Long, ExecutionNodeRepository.NodeLastRun> latestRunsByNode() {
            return java.util.Map.of();
        }


        @Override
        public void updateAgentBaseUrl(long id, String agentBaseUrl) {
            throw new UnsupportedOperationException("桩不支持地址更新");
        }
    }

    /** 记录两类提交的派发器桩。 */
    private static final class RecordingDispatcher implements RunDispatcher {
        private final List<String> submits = new java.util.ArrayList<>();
        private final List<NodeSubmit> nodeSubmits = new java.util.ArrayList<>();

        @Override
        public void submit(long projectId, Long planId, String runId, int priority, Runnable task) {
            submits.add(runId);
        }

        @Override
        public void submitToNode(long projectId, long nodeId, int nodeConcurrency, Long planId,
                                 String runId, int priority, Runnable task) {
            nodeSubmits.add(new NodeSubmit(nodeId, nodeConcurrency, runId));
        }
    }

    private record NodeSubmit(long nodeId, int nodeConcurrency, String runId) {
    }
}

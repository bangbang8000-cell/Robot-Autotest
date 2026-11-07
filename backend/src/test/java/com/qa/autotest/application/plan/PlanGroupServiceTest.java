package com.qa.autotest.application.plan;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.PlanGroupRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.plan.PlanGroup;
import com.qa.autotest.domain.plan.PlanRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计划组（ORCH-01）：链式建边、幂等、整图环检测、跨项目拒绝。
 *
 * <p>不连库、不启 Spring：仓储用内存伪实现，权限与审计用桩。
 * 成环用例刻意让组内顺序与既有边相反——跨组的环最容易漏，只测组内自环没有意义。
 */
class PlanGroupServiceTest {

    private static final long PROJECT = PlanTestSupport.PROJECT;

    private PlanTestSupport.FakePlanRepository plans;
    private FakeGroupRepository groups;
    private PlanGroupService service;

    @BeforeEach
    void setUp() {
        plans = new PlanTestSupport.FakePlanRepository();
        for (long id = 1; id <= 5; id++) {
            plans.put(PlanTestSupport.plan(id, com.qa.autotest.domain.plan.PlanStatus.ENABLED,
                    null, null, "0 0 3 * * *", List.of("suites/a.robot")));
        }
        groups = new FakeGroupRepository(plans);
        service = new PlanGroupService(groups, plans, new PermissiveAccess(), new SilentAudit());
    }

    @Test
    @DisplayName("3 个计划成组，应用链式依赖产生 2 条边")
    void 三成员两条边() {
        PlanGroupView group = service.create(command("daily-chain"));
        group = service.addMembers(PROJECT, group.id(), List.of(1L, 2L, 3L));
        assertEquals(3, group.items().size());
        assertEquals(2, group.edgeCount());

        PlanGroupView.ApplyResult result = service.applyChain(PROJECT, group.id());

        assertEquals(2, result.created());
        assertEquals(0, result.skipped());
        // 后一条依赖前一条：2->1、3->2
        assertEquals(List.of(1L), plans.dependencies(PROJECT, 2L));
        assertEquals(List.of(2L), plans.dependencies(PROJECT, 3L));
        assertTrue(plans.dependencies(PROJECT, 1L).isEmpty(), "首个成员不应有组内前置");
    }

    @Test
    @DisplayName("重复应用幂等：第二次 created=0，边数不变")
    void 重复应用幂等() {
        PlanGroupView group = service.addMembers(PROJECT,
                service.create(command("idem")).id(), List.of(1L, 2L, 3L));

        service.applyChain(PROJECT, group.id());
        PlanGroupView.ApplyResult second = service.applyChain(PROJECT, group.id());

        assertEquals(0, second.created());
        assertEquals(2, second.skipped());
        assertEquals(1, plans.dependencies(PROJECT, 2L).size());
        assertEquals(1, plans.dependencies(PROJECT, 3L).size());
    }

    @Test
    @DisplayName("组内顺序与既有边成环时被拒，错误文案带计划名")
    void 成环被拒且带计划名() {
        // 既有边：1 依赖 2（plan-1 -> plan-2）。组内顺序 [1, 2] 会再加 2 依赖 1，形成 1→2→1。
        plans.depend(1L, 2L);
        PlanGroupView group = service.addMembers(PROJECT,
                service.create(command("cycle")).id(), List.of(1L, 2L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.applyChain(PROJECT, group.id()));

        assertEquals(ErrorCode.INVALID_ARGUMENT, error.errorCode());
        assertTrue(error.getMessage().contains("plan-1"), "应带计划名，实际：" + error.getMessage());
        assertTrue(error.getMessage().contains("plan-2"), "应带计划名，实际：" + error.getMessage());
        // 失败不落库：原有的 1 条边还在，没有多出 2 依赖 1
        assertEquals(List.of(2L), plans.dependencies(PROJECT, 1L));
        assertTrue(plans.dependencies(PROJECT, 2L).isEmpty());
    }

    @Test
    @DisplayName("跨项目引用计划返回 403 而不是 400")
    void 跨项目拒绝() {
        groups.otherProjectPlans.add(9L);
        PlanGroupView group = service.create(command("cross"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.addMembers(PROJECT, group.id(), List.of(9L)));

        assertEquals(ErrorCode.FORBIDDEN, error.errorCode());
    }

    @Test
    @DisplayName("重复加入同一计划不报错、不重复插")
    void 重复加入成员幂等() {
        PlanGroupView group = service.create(command("dup"));
        service.addMembers(PROJECT, group.id(), List.of(1L, 2L));
        PlanGroupView again = service.addMembers(PROJECT, group.id(), List.of(2L, 3L));

        assertEquals(3, again.items().size());
        assertEquals(List.of(1L, 2L, 3L),
                again.items().stream().map(PlanGroupView.Member::planId).toList());
    }

    @Test
    @DisplayName("组名重复返回冲突")
    void 组名重复() {
        service.create(command("same-name"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(command("same-name")));
        assertEquals(ErrorCode.CONFLICT, error.errorCode());
    }

    @Test
    @DisplayName("重排是全量语义：传入顺序即新顺序")
    void 重排全量语义() {
        PlanGroupView group = service.addMembers(PROJECT,
                service.create(command("reorder")).id(), List.of(1L, 2L, 3L));

        PlanGroupView reordered = service.reorder(PROJECT, group.id(), List.of(3L, 1L, 2L));

        assertEquals(List.of(3L, 1L, 2L),
                reordered.items().stream().map(PlanGroupView.Member::planId).toList());
        assertEquals(List.of(0, 1, 2),
                reordered.items().stream().map(PlanGroupView.Member::sortOrder).toList());
    }

    @Test
    @DisplayName("N8：重排后自动应用链式依赖，边跟着新顺序走")
    void 重排后自动应用() {
        PlanGroupView group = service.addMembers(PROJECT,
                service.create(command("reorder-apply")).id(), List.of(1L, 2L, 3L));

        service.reorder(PROJECT, group.id(), List.of(3L, 1L, 2L));

        assertEquals(List.of(3L), plans.dependencies(PROJECT, 1L));
        assertEquals(List.of(1L), plans.dependencies(PROJECT, 2L));
        assertTrue(plans.dependencies(PROJECT, 3L).isEmpty(), "新顺序的首个成员不应有组内前置");
    }

    @Test
    @DisplayName("N8：二次重排先拆反向旧边，不会因上次应用留下的边成环")
    void 重排可反复执行() {
        PlanGroupView group = service.addMembers(PROJECT,
                service.create(command("reorder-twice")).id(), List.of(1L, 2L, 3L));

        service.applyChain(PROJECT, group.id());          // 2<-1、3<-2
        service.reorder(PROJECT, group.id(), List.of(3L, 2L, 1L));

        // 新顺序 3 -> 2 -> 1：1 依赖 2、2 依赖 3；旧的两条反向边必须被拆掉
        assertEquals(List.of(3L), plans.dependencies(PROJECT, 2L));
        assertEquals(List.of(2L), plans.dependencies(PROJECT, 1L));
        assertTrue(plans.dependencies(PROJECT, 3L).isEmpty());
    }

    @Test
    @DisplayName("N8：单成员组重排不建边也不报错")
    void 单成员重排跳过应用() {
        PlanGroupView group = service.addMembers(PROJECT,
                service.create(command("single-reorder")).id(), List.of(1L));

        service.reorder(PROJECT, group.id(), List.of(1L));

        assertTrue(plans.dependencies(PROJECT, 1L).isEmpty());
    }

    @Test
    @DisplayName("单个成员的组没有边可建，直接拒绝而不是静默成功")
    void 单成员拒绝建边() {
        PlanGroupView group = service.addMembers(PROJECT,
                service.create(command("single")).id(), List.of(1L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.applyChain(PROJECT, group.id()));
        assertEquals(ErrorCode.INVALID_ARGUMENT, error.errorCode());
        assertTrue(error.getMessage().contains("至少需要 2"));
    }

    private static PlanGroupService.GroupCommand command(String name) {
        return new PlanGroupService.GroupCommand(PROJECT, name, null);
    }

    /** 内存组仓储：顺序语义与 JDBC 实现一致（append 接末尾、reorder 全量覆盖）。 */
    static final class FakeGroupRepository implements PlanGroupRepository {

        private final Map<Long, PlanGroup> store = new LinkedHashMap<>();
        private final Map<Long, List<PlanGroup.PlanGroupItem>> items = new LinkedHashMap<>();
        private final PlanTestSupport.FakePlanRepository plans;
        final LinkedHashSet<Long> otherProjectPlans = new LinkedHashSet<>();
        private final AtomicLong ids = new AtomicLong();

        FakeGroupRepository(PlanTestSupport.FakePlanRepository plans) {
            this.plans = plans;
        }

        @Override
        public List<PlanGroup> listByProject(long projectId) {
            return store.values().stream()
                    .map(group -> withItems(group.id()))
                    .toList();
        }

        @Override
        public Optional<PlanGroup> find(long projectId, long id) {
            return store.containsKey(id) ? Optional.of(withItems(id)) : Optional.empty();
        }

        @Override
        public Optional<PlanGroup> findByName(long projectId, String name) {
            return store.values().stream()
                    .filter(group -> group.name().equals(name))
                    .findFirst();
        }

        @Override
        public long insert(PlanGroup draft, Long operatorId) {
            long id = ids.incrementAndGet();
            store.put(id, new PlanGroup(id, draft.projectId(), draft.name(), draft.description(),
                    List.of(), operatorId, PlanTestSupport.CLOCK.instant()));
            items.put(id, new ArrayList<>());
            return id;
        }

        @Override
        public void update(long projectId, long id, String name, String description, Long operatorId) {
            PlanGroup current = withItems(id);
            store.put(id, new PlanGroup(id, current.projectId(), name, description,
                    current.items(), current.createdBy(), current.createdAt()));
        }

        @Override
        public void delete(long projectId, long id) {
            store.remove(id);
            items.remove(id);
        }

        @Override
        public int appendItems(long projectId, long groupId, List<Long> planIds, Long operatorId) {
            List<PlanGroup.PlanGroupItem> current = items.computeIfAbsent(groupId, key -> new ArrayList<>());
            int inserted = 0;
            for (Long planId : planIds) {
                boolean exists = current.stream().anyMatch(item -> item.planId() == planId);
                if (exists) {
                    continue;
                }
                current.add(new PlanGroup.PlanGroupItem(planId, nameOf(planId), current.size()));
                inserted++;
            }
            return inserted;
        }

        @Override
        public void removeItem(long projectId, long groupId, long planId) {
            items.computeIfAbsent(groupId, key -> new ArrayList<>())
                    .removeIf(item -> item.planId() == planId);
        }

        @Override
        public void reorder(long projectId, long groupId, List<Long> planIds, Long operatorId) {
            List<PlanGroup.PlanGroupItem> reordered = new ArrayList<>();
            for (int i = 0; i < planIds.size(); i++) {
                long planId = planIds.get(i);
                reordered.add(new PlanGroup.PlanGroupItem(planId, nameOf(planId), i));
            }
            items.put(groupId, reordered);
        }

        @Override
        public boolean existsInOtherProject(long projectId, long planId) {
            return otherProjectPlans.contains(planId);
        }

        private PlanGroup withItems(long id) {
            PlanGroup group = store.get(id);
            List<PlanGroup.PlanGroupItem> members = items.getOrDefault(id, List.of());
            List<PlanGroup.PlanGroupItem> sorted = members.stream()
                    .sorted((left, right) -> Integer.compare(left.sortOrder(), right.sortOrder()))
                    .toList();
            return new PlanGroup(group.id(), group.projectId(), group.name(), group.description(),
                    sorted, group.createdBy(), group.createdAt());
        }

        private String nameOf(long planId) {
            return plans.store.values().stream()
                    .filter(plan -> plan.id() == planId)
                    .map(PlanRecord::name)
                    .findFirst()
                    .orElse("#" + planId);
        }
    }

    /** 权限桩：bindProject 不做事（跨项目由 existsInOtherProject 单独判定）。 */
    static final class PermissiveAccess extends AccessControlService {

        PermissiveAccess() {
            super(null, null);
        }

        @Override
        public void bindProject(long projectId) {
            // 测试里不做项目成员校验
        }

        @Override
        public void require(PermissionResource resource, PermissionAction action) {
            // 测试里不做角色校验
        }
    }

    /** 审计桩：只收集，不落库。 */
    static final class SilentAudit extends com.qa.autotest.application.iam.AuditService {

        final List<String> actions = new ArrayList<>();

        SilentAudit() {
            super(null);
        }

        @Override
        public synchronized void record(String action, String resourceType, String resourceId,
                                        String detail) {
            actions.add(action);
        }
    }
}

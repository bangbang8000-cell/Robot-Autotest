package com.qa.autotest.application.execution;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.FailureResultRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.execution.FailureClass;
import com.qa.autotest.domain.execution.FailureClusterKey;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.execution.RunRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 失败聚类（ORCH-03，v2.1）。
 *
 * <p>三件事分开做，避免互相污染：
 * <ol>
 *   <li><b>分类</b>复用既有 {@link FailureClassifier}（CANCELED &gt; ENV &gt; INFRA &gt; ASSERT），
 *       本类不重写规则，只把用例级失败文本喂进去；</li>
 *   <li><b>簇键</b>由 {@link FailureClusterKey} 归一化，纯确定性；</li>
 *   <li><b>排序</b>固定为「类别枚举序 → 簇条数倒序 → 簇键字典序」，
 *       不用 HashMap 迭代序、不按时间，保证同一输入两次输出逐字节一致。</li>
 * </ol>
 *
 * <p>为什么不落库：簇是视图。归一化规则一改，历史簇就该重算；落库会让新旧规则算出的簇并存。
 */
@Service
public class FailureClusterService {

    /** 每簇最多展示几条样例：超过 3 条在表格里已经看不清，且用户只需要判断「是不是同一回事」。 */
    private static final int SAMPLE_LIMIT = 3;

    private final FailureResultRepository results;
    private final FailureClassifier classifier;
    private final AccessControlService access;
    private final CaseRunService caseRuns;

    public FailureClusterService(FailureResultRepository results,
                                 FailureClassifier classifier,
                                 AccessControlService access,
                                 CaseRunService caseRuns) {
        this.results = results;
        this.classifier = classifier;
        this.access = access;
        this.caseRuns = caseRuns;
    }

    /**
     * 聚一轮的失败。
     *
     * @param runId 运行业务 ID（{@code run.run_id}）
     */
    public FailureClusterView cluster(long projectId, String runId) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }

        List<FailureResultRepository.FailureRow> rows = results.failedRows(projectId, runId);
        FailureResultRepository.RunFailureContext context = results.runContext(projectId, runId);

        // 类别 → 簇键 → 行。TreeMap 保证簇键有序，EnumMap 保证类别按声明序。
        Map<FailureClass, Map<String, List<FailureResultRepository.FailureRow>>> grouped =
                new EnumMap<>(FailureClass.class);
        final int total = rows.size();
        for (FailureResultRepository.FailureRow row : rows) {
            FailureClass failureClass = classify(row.failureMsg());
            String key = FailureClusterKey.of(row.failureMsg());
            grouped.computeIfAbsent(failureClass, ignored -> new TreeMap<>())
                    .computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(row);
        }

        List<FailureClusterView.ClassGroup> classGroups = new ArrayList<>();
        for (FailureClass failureClass : FailureClass.values()) {
            Map<String, List<FailureResultRepository.FailureRow>> clusters =
                    grouped.get(failureClass);
            if (clusters == null || clusters.isEmpty()) {
                continue;
            }
            int classCount = clusters.values().stream().mapToInt(List::size).sum();
            List<FailureClusterView.Cluster> views = new ArrayList<>(clusters.size());
            clusters.entrySet().stream()
                    .sorted(clusterOrder())
                    .forEach(entry -> views.add(toCluster(entry.getKey(), entry.getValue(), total)));
            classGroups.add(new FailureClusterView.ClassGroup(failureClass.name(), classCount,
                    ratio(classCount, total), views));
        }

        return new FailureClusterView(runId, total,
                context == null ? null : context.runStatus(),
                context == null ? null : context.failureClass(),
                classGroups);
    }

    /**
     * 按簇重跑：把该簇的用例 id 交给既有 {@link CaseRunService}，
     * 安全级拦截（L2 / LX）由它判，本类不做第二套判断。
     */
    public RunRecord rerun(long projectId, String runId, String clusterKey) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.EXECUTE);
        FailureClusterView view = cluster(projectId, runId);
        for (FailureClusterView.ClassGroup group : view.classes()) {
            for (FailureClusterView.Cluster cluster : group.clusters()) {
                if (cluster.key().equals(clusterKey)) {
                    if (cluster.caseIds().isEmpty()) {
                        throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                                "该簇没有关联用例，无法重跑: " + clusterKey);
                    }
                    return caseRuns.run(new CaseRunService.CaseRunCommand(projectId,
                            cluster.caseIds(), null, List.of(), null, null, null, null, null));
                }
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "簇不存在: " + clusterKey);
    }

    /** 用例级分类：只喂失败文本，产物视为可用（用例行没有产物上下文）。 */
    private FailureClass classify(String failureMsg) {
        return classifier.classify(new FailureClassifier.FailureInput(
                false, null, failureMsg, null, null, null, true));
    }

    private static FailureClusterView.Cluster toCluster(String key,
                                                        List<FailureResultRepository.FailureRow> rows,
                                                        int total) {
        List<FailureClusterView.Sample> samples = rows.stream()
                .limit(SAMPLE_LIMIT)
                .map(row -> new FailureClusterView.Sample(row.caseId(), row.caseCode(), row.caseTitle()))
                .toList();
        List<Long> caseIds = rows.stream()
                .map(FailureResultRepository.FailureRow::caseId)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new FailureClusterView.Cluster(key, rows.size(), ratio(rows.size(), total),
                samples, caseIds);
    }

    /** 簇排序：条数倒序，同条数按簇键字典序（稳定，不依赖插入顺序）。 */
    private static Comparator<Map.Entry<String, List<FailureResultRepository.FailureRow>>> clusterOrder() {
        return Comparator
                .<Map.Entry<String, List<FailureResultRepository.FailureRow>>>comparingInt(
                        entry -> -entry.getValue().size())
                .thenComparing(entry -> entry.getKey(), FailureClusterKey::compare);
    }

    /** 占比保留 4 位小数：够表达 1/3 与 1/10000，又不引入浮点尾差。 */
    private static double ratio(int part, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round(part * 10000.0 / total) / 10000.0;
    }
}

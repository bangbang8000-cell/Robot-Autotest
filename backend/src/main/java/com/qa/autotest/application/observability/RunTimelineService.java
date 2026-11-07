package com.qa.autotest.application.observability;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ExecutionShardRepository;
import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.execution.ExecutionShard;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 运行时间线（W4）：把 run 自身的三个时间点与每个分片的领取/收口时间点，
 * 合并成一条按时间排序的事件流，供前端「执行过程」视图直接渲染。
 *
 * <p>只读派生，不落库：数据源是 {@code run.enqueued_at/started_at/finished_at}
 * 与 {@code execution_shard.started_at/finished_at}。事件类型交给前端做 i18n，
 * 后端只出结构化字段（分片号、节点、尝试次数、fencing 令牌、用例数/失败数）。
 */
@Service
public class RunTimelineService {

    private final RunRepository runs;
    private final ExecutionShardRepository shards;
    private final AccessControlService access;

    public RunTimelineService(RunRepository runs, ExecutionShardRepository shards,
                              AccessControlService access) {
        this.runs = runs;
        this.shards = shards;
        this.access = access;
    }

    /** 单条事件；run 级事件的 {@code shardIndex = -1}、{@code nodeId = null}。 */
    public record TimelineEvent(String at, String type, int shardIndex, Long nodeId,
                                int attempt, long fencingToken, int testCount, int failCount) {
    }

    public record RunTimelineView(String runId, String state, String enqueuedAt, String startedAt,
                                  String finishedAt, List<TimelineEvent> events) {
    }

    public RunTimelineView timeline(long projectId, String runId) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);

        RunRecord run = runs.findByRunId(projectId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "运行不存在: " + runId));
        List<ExecutionShard> rows = shards.listByRun(projectId, runId);

        List<Sortable> sortable = new ArrayList<>();
        if (run.enqueuedAt() != null) {
            sortable.add(new Sortable(run.enqueuedAt(), new TimelineEvent(
                    run.enqueuedAt().toString(), "RUN_ENQUEUED", -1, null, 0, 0, 0, 0)));
        }
        if (run.startedAt() != null) {
            sortable.add(new Sortable(run.startedAt(), new TimelineEvent(
                    run.startedAt().toString(), "RUN_STARTED", -1, null, 0, 0, 0, 0)));
        }
        for (ExecutionShard row : rows) {
            if (row.startedAt() != null) {
                sortable.add(new Sortable(row.startedAt(), new TimelineEvent(
                        row.startedAt().toString(), "SHARD_LEASED", row.shardIndex(), row.nodeId(),
                        row.attempt(), row.fencingToken(), 0, 0)));
            }
            if (row.finishedAt() != null) {
                sortable.add(new Sortable(row.finishedAt(), new TimelineEvent(
                        row.finishedAt().toString(), shardEndType(row), row.shardIndex(), row.nodeId(),
                        row.attempt(), row.fencingToken(), row.testCount(), row.failCount())));
            }
        }
        if (run.finishedAt() != null) {
            sortable.add(new Sortable(run.finishedAt(), new TimelineEvent(
                    run.finishedAt().toString(), "RUN_FINISHED", -1, null, 0, 0,
                    run.totalTests(), run.failedTests())));
        }
        sortable.sort(Comparator.comparing(Sortable::at));
        List<TimelineEvent> events = sortable.stream().map(Sortable::event).toList();

        // 库里的 run_status 是小写（canceled/finished…），接口统一出大写，
        // 与前端 TERMINAL 判定、分片看板的 aggregateState 口径一致。
        return new RunTimelineView(runId, run.state().dbValue().toUpperCase(Locale.ROOT),
                toStringOrNull(run.enqueuedAt()), toStringOrNull(run.startedAt()),
                toStringOrNull(run.finishedAt()), events);
    }

    private static String shardEndType(ExecutionShard row) {
        return switch (row.state()) {
            case ExecutionShard.DONE -> "SHARD_DONE";
            case ExecutionShard.FAILED -> "SHARD_FAILED";
            case ExecutionShard.LOST -> "SHARD_LOST";
            default -> "SHARD_UPDATED";
        };
    }

    private static String toStringOrNull(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private record Sortable(Instant at, TimelineEvent event) {
    }
}

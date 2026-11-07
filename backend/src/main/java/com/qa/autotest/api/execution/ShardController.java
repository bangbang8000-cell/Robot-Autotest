package com.qa.autotest.api.execution;

import com.qa.autotest.application.execution.ShardService;
import com.qa.autotest.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分片看板接口（W2，FR-v2.2-05）。
 *
 * <p>只放**平台侧**读写（分片列表、规划、聚合）；agent 的 claim / complete 在
 * {@link com.qa.autotest.api.node.NodeController}，因为那两条走节点令牌而非用户会话。
 *
 * <p>规划是幂等重建：同一 run 重复调用以最后一次为准（规划属于主控动作，
 * 不参与 fencing——fencing 只保护**执行结果**的回写）。
 */
@RestController
@RequestMapping("/api/execution")
public class ShardController {

    private final ShardService shards;

    public ShardController(ShardService shards) {
        this.shards = shards;
    }

    /** 规划请求：suitePaths 为本轮全部套件路径，nodeCount 缺省按上限 4 切。 */
    public record PlanShardRequest(List<String> suitePaths, Integer nodeCount) {
    }

    @GetMapping("/runs/{runId}/shards")
    public ApiResponse<ShardService.RunShardsView> list(@PathVariable String runId,
                                                        @RequestParam long projectId) {
        return ApiResponse.ok(shards.view(projectId, runId));
    }

    @PostMapping("/runs/{runId}/shards/plan")
    public ApiResponse<ShardService.RunShardsView> plan(@PathVariable String runId,
                                                        @RequestParam long projectId,
                                                        @RequestBody PlanShardRequest request) {
        return ApiResponse.ok(shards.plan(projectId, runId, request.suitePaths(),
                request.nodeCount(), null));
    }

    @PostMapping("/runs/{runId}/shards/aggregate")
    public ApiResponse<ShardService.RunShardsView> aggregate(@PathVariable String runId,
                                                             @RequestParam long projectId) {
        return ApiResponse.ok(shards.aggregate(projectId, runId));
    }
}

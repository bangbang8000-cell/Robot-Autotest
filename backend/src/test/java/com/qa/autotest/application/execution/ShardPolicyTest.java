package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.ExecutionShardRepository.ShardDraft;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 分片策略单测（W2）：不重不漏 + 退化 + 节点上限。 */
class ShardPolicyTest {

    @Test
    void singleShardWhenLessThanTwoSuites() {
        List<ShardDraft> drafts = ShardPolicy.plan(List.of("suites/a.robot"), 4, 4);
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).suitePaths()).containsExactly("suites/a.robot");
    }

    @Test
    void singleShardWhenOneNode() {
        List<ShardDraft> drafts = ShardPolicy.plan(
                List.of("a", "b", "c"), 1, 4);
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).suitePaths()).containsExactly("a", "b", "c");
    }

    @Test
    void fourNodesSplitEightSuitesWithoutDuplicateOrLoss() {
        List<String> suites = List.of(
                "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8");
        List<ShardDraft> drafts = ShardPolicy.plan(suites, 4, 4);

        assertThat(drafts).hasSize(4);
        assertThat(drafts).extracting(ShardDraft::shardIndex)
                .containsExactly(0, 1, 2, 3);
        Set<String> union = new HashSet<>();
        int total = 0;
        for (ShardDraft draft : drafts) {
            assertThat(draft.suitePaths()).isNotEmpty();
            Set<String> one = new HashSet<>(draft.suitePaths());
            assertThat(one).hasSize(draft.suitePaths().size()); // 片内不重
            union.addAll(one);
            total += draft.suitePaths().size();
        }
        assertThat(union).containsExactlyInAnyOrderElementsOf(suites); // 片间不重、不漏
        assertThat(total).isEqualTo(suites.size());
    }

    @Test
    void nodeCountAboveFourIsClamped() {
        List<String> suites = List.of("a", "b", "c", "d", "e");
        List<ShardDraft> drafts = ShardPolicy.plan(suites, 8, ShardPolicy.MAX_NODES);
        assertThat(drafts).hasSize(4);
    }

    @Test
    void unbalancedSuiteCountStillCoversAll() {
        // 3 个套件切 4 片：最多 3 片，且不能出现空片
        List<ShardDraft> drafts = ShardPolicy.plan(List.of("a", "b", "c"), 4, 4);
        assertThat(drafts).hasSize(3);
        assertThat(drafts).flatExtracting(ShardDraft::suitePaths)
                .containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void emptyInputIsRejected() {
        assertThatThrownBy(() -> ShardPolicy.plan(List.of(), 4, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

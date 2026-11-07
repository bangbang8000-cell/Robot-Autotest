package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.ExecutionShardRepository.ShardDraft;

import java.util.ArrayList;
import java.util.List;

/**
 * 分片策略（W2，FR-v2.2-02）。
 *
 * <p>三条口径：
 * <ol>
 *   <li><b>按套件切</b>，不按用例切——Robot 的 suite 是执行与报告的最小边界，
 *       切到 test 级会破坏 output.xml 的套件结构；</li>
 *   <li><b>节点数上限 4</b>（规划文档 §4 W2）：上限是运维可解释的容量口径，
 *       不是技术限制，超过 4 台后先把本机并发调满再谈扩节点；</li>
 *   <li><b>退化为单分片</b>：套件 &lt; 2 或节点 &lt; 2 时不分片，保持 v2.1
 *       「一轮一节点」的语义——分片是可选能力，不是新默认。</li>
 * </ol>
 *
 * <p>切法是连续等分（相邻套件同片），保证「不重不漏」：
 * 每片路径集合互不相交，并集等于输入。
 */
public final class ShardPolicy {

    /** 参与分片的执行节点数上限（超过按 4 计）。 */
    public static final int MAX_NODES = 4;

    private ShardPolicy() {
    }

    /**
     * 生成分片草稿。
     *
     * @param suitePaths 本轮全部套件路径（保持调用方顺序）
     * @param nodeCount  可用节点数（1 台或未指定 → 单分片）
     * @param maxNodes   节点数上限（生产传 {@link #MAX_NODES}；单测可传更小值）
     */
    public static List<ShardDraft> plan(List<String> suitePaths, int nodeCount, int maxNodes) {
        if (suitePaths == null || suitePaths.isEmpty()) {
            throw new IllegalArgumentException("suitePaths 不能为空");
        }
        List<String> distinct = suitePaths.stream().distinct().toList();
        int nodes = Math.max(1, Math.min(nodeCount, Math.max(1, maxNodes)));
        if (distinct.size() < 2 || nodes < 2) {
            return List.of(new ShardDraft(0, distinct));
        }
        int shards = Math.min(nodes, distinct.size());
        int base = distinct.size() / shards;
        int extra = distinct.size() % shards;
        List<ShardDraft> drafts = new ArrayList<>(shards);
        int cursor = 0;
        for (int i = 0; i < shards; i++) {
            int size = base + (i < extra ? 1 : 0);
            drafts.add(new ShardDraft(i, List.copyOf(distinct.subList(cursor, cursor + size))));
            cursor += size;
        }
        return List.copyOf(drafts);
    }
}

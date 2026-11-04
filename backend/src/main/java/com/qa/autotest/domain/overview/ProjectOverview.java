package com.qa.autotest.domain.overview;

import java.time.Instant;
import java.util.List;

/**
 * 项目总览快照（OVW-01，PRD §3.4）。
 *
 * <p>口径以 {@code docs/plan/总览页真实化-评估与设计-20261104.md} §4 为准，改口径必须先改那份文档。
 * 三条最容易算错的规则写在这里，防止后来者「顺手优化」掉：
 * <ul>
 *   <li>「完成」= {@code run_status IN ('finished','failed')}；取消/排队/运行中一律不进通过率分母。</li>
 *   <li>通过率是<b>用例级</b>（{@code test_result} 明细），不是 run 级；无样本时为 {@code null}，
 *       <b>不得</b>写成 0——0% 会被读成「跑过且全挂」。</li>
 *   <li>通过率单位是<b>基点</b>（1/10000），避免浮点在序列化里出现 {@code 66.66666666666667}。</li>
 * </ul>
 */
public record ProjectOverview(
        long projectId,
        Instant generatedAt,
        int windowDays,
        CaseStats cases,
        AssetStats assets,
        RunStats runs,
        List<RecentRun> recentRuns,
        List<DailyPoint> dailyTrend) {

    /** 分组计数。key 为展示名（空值已归一到「未标注」），count 为行数。 */
    public record CountItem(String key, int count) {
    }

    /**
     * 用例级通过率（基点，1/10000）。样本为 0 时返回 {@code null}——「没跑过」和「全挂」必须可区分。
     *
     * <p>公式只在这里定义一次：总览卡片与近期执行行都调它，避免两处各写一份后慢慢漂移。
     */
    public static Integer passRateBp(int pass, int fail, int skip) {
        int total = pass + fail + skip;
        if (total <= 0) {
            return null;
        }
        return (int) Math.round(pass * 10000.0 / total);
    }

    /** 用例统计。 */
    public record CaseStats(int total,
                            List<CountItem> byPriority,
                            List<CountItem> byReviewStatus,
                            List<CountItem> topDomains) {
        public static CaseStats empty() {
            return new CaseStats(0, List.of(), List.of(), List.of());
        }
    }

    /** 资产统计。端口表无 project_id，通过 device 关联取租户。 */
    public record AssetStats(int devices, int ports, int links,
                             int credentials, int environments, int testSets) {
        public static AssetStats empty() {
            return new AssetStats(0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * 运行计数（仓储原始值）。派生的通过率/平均耗时由应用层计算，
     * 仓储不掺业务口径，只做 GROUP BY。
     */
    public record RunCounts(int total, int inWindow, int running,
                            int finished, int failed, int canceled,
                            int casePass, int caseFail, int caseSkip,
                            Double avgDurationMs, Instant lastRunAt) {
        public static RunCounts empty() {
            return new RunCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, null, null);
        }
    }

    /** 执行统计（已派生）。 */
    public record RunStats(int total, int inWindow, int running,
                           int finished, int failed, int canceled,
                           int casePass, int caseFail, int caseSkip,
                           Integer passRateBp, Long avgDurationMs, Instant lastRunAt) {
        public static RunStats empty() {
            return new RunStats(0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null);
        }
    }

    /** 近期执行的一行。通过率同为基点，样本不足时为 null。 */
    public record RecentRun(String runId, String state,
                            Instant startedAt, Instant finishedAt, Long durationMs,
                            int total, int passed, int failed,
                            String dut, String link, String testSetName,
                            String triggerType, Integer passRateBp) {
    }

    /** 近 N 天的一个数据点：三个计数都是<b>运行数</b>，不是用例数。 */
    public record DailyPoint(String date, int finished, int failed, int canceled) {
    }
}

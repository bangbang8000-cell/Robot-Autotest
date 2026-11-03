-- 试运行观察窗：近 7 天按日统计（HRD-03 / DEV-PLAN §6.1）
--
-- 与 DEV-PLAN 伪代码的字段映射（按真实 V1~V7_1 结构落地，不另造列）：
--   DEV-PLAN: state='finished' → 实际: run.run_status='passed'
--            （本库 run_status 取值 created/running/passed/failed/cancelled，
--              没有 finished 状态；"成功"取 passed，失败/取消仍计入分母）
--   DEV-PLAN: SUM(exportable)  → 实际: run.exportable 由 V5_1 建立，
--            TINYINT(1)，1 = 十项 metadata 齐全且无超长，可直接对外交付
-- 口径：started_at 为空的 run（只创建未启动）不进窗口。
--
-- 用法：
--   mysql -h127.0.0.1 -P3306 -urobot -p robot_dev < scripts/window-stats.sql
-- 机器可读（无表头/无对齐）：加 --batch --skip-column-names

SELECT DATE(r.started_at)                                       AS d,
       COUNT(*)                                                 AS runs,
       SUM(r.run_status = 'passed')                             AS ok,
       ROUND(SUM(r.run_status = 'passed') / COUNT(*) * 100, 1)   AS success_rate,
       SUM(r.exportable = 1)                                    AS exportable_runs
FROM `run` r
WHERE r.started_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY DATE(r.started_at)
ORDER BY d;

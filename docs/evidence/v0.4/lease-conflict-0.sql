[待149执行]
-- v0.4 执行域 · 租约冲突 0 证明（EXE-02）
-- 目的：用 SQL 结果回答「并发申请后，是否出现同一资源被两方同时持有」。
-- 环境：149 上的 MariaDB（库名默认 robot_dev，账号 robot，口令走 DB_PASSWORD 环境变量）。
--
-- 执行方式（在 149 上，仓库根目录）：
--   mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 \
--         -u robot -p robot_dev < docs/evidence/v0.4/lease-conflict-0.sql
--
-- 判定：Q2 返回空集、Q3 计数为 0，即「0 冲突」；Q1 的数字应与当前执行中的运行数一致。

-- Q1：当前持有租约行数（held 状态），唯一键下每个 resource_key 至多 1 行
SELECT COUNT(*) AS held_rows
  FROM resource_lease
 WHERE lease_state = 'held';

-- Q2：同一 resource_key 出现多行的资源（唯一键 uk_resource_lease_key 下恒为空集）
SELECT resource_key, COUNT(*) AS rows_per_key
  FROM resource_lease
 GROUP BY resource_key
HAVING COUNT(*) > 1;

-- Q3：历史表中「同一资源同一时刻被两次持有」的重叠对数量（应为 0）
--     重叠定义：A 的持有区间 [acquired_at, released_at) 与 B 的持有区间相交，且 job_id 不同
SELECT COUNT(*) AS overlapping_pairs
  FROM resource_lease_history a
  JOIN resource_lease_history b
    ON a.resource_key = b.resource_key
   AND a.job_id <> b.job_id
   AND a.acquired_at < COALESCE(b.released_at, NOW(3))
   AND b.acquired_at < COALESCE(a.released_at, NOW(3));

-- Q4：历史表按状态分布，用于确认释放/回收都留了痕
SELECT lease_state, COUNT(*) AS rows_count
  FROM resource_lease_history
 GROUP BY lease_state;

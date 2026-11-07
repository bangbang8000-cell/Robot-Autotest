-- V10_1__audit_keyset.sql  ·  v2.0 收口 B5：百万行分页（审计）与 case_key 索引评估
-- 归属 B5 ｜ 证据 docs/evidence/v2.0/b5-keyset/（100 万行实测 + EXPLAIN）
--
-- 1) audit_log：管理员全局查询（不带 project_id）没有 (occurred_at, id) 索引，
--    100 万行实测 LIMIT 20 OFFSET 500000 = 1272ms，keyset 也仍 836ms（全表 + filesort）；
--    加 idx_audit_time_id 后 keyset = 39ms（range + 反向索引扫描）。
--    项目内查询已有 idx_audit_log_project_time(project_id, occurred_at)，InnoDB 二级索引
--    隐含主键 id（EXPLAIN key_len=24 已含 id），所以**不再加** (project_id, occurred_at, id)：
--    那是等价索引，只白占空间（实测 keyset 已走旧索引 46ms）。
--
-- 2) test_result.case_key 无索引：趋势/对比按 case_key 查询，100 万行实测 474ms 全表扫描
--    + 临时表 + filesort；加 (case_key, run_id) 后 36ms。
ALTER TABLE `audit_log` ADD INDEX idx_audit_time_id (occurred_at, id);
ALTER TABLE `test_result` ADD INDEX idx_test_result_case_key (case_key, run_id);

-- V2_4__audit_indexes.sql  ·  v0.2 审计索引
-- 归属 IAM-01 / IAM-05 ｜ 对应 NFR-v0.2-06（关键写操作 100% 落 audit_log）
-- 只加索引，不加表；查询 UI 在 v1.0（HRD-02）。
ALTER TABLE `audit_log` ADD INDEX idx_audit_resource (resource_type, resource_id);
ALTER TABLE `audit_log` ADD INDEX idx_audit_action_time (action, occurred_at);
ALTER TABLE `audit_log` ADD INDEX idx_audit_project_actor (project_id, actor_id, occurred_at);

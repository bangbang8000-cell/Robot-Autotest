-- V2_4__audit_indexes.down.sql  ·  回退 v0.2 审计索引
ALTER TABLE `audit_log` DROP INDEX idx_audit_project_actor;
ALTER TABLE `audit_log` DROP INDEX idx_audit_action_time;
ALTER TABLE `audit_log` DROP INDEX idx_audit_resource;

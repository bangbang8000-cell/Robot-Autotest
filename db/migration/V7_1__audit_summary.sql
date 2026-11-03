-- V7_1__audit_summary.sql  ·  v1.0 审计查询与变更摘要（HRD-02）
-- 归属 IAM-05 / FR-v1.0-06 ｜ 对应 DEV-PLAN §3.2
-- 只加列，不改表结构语义；审计仍为只增不改（不提供 DELETE / UPDATE 接口）。
ALTER TABLE `audit_log`
  ADD COLUMN `operator` VARCHAR(64) NULL COMMENT '操作人用户名快照，避免改名后无法追溯' AFTER `actor_id`,
  ADD COLUMN `summary` TEXT NULL COMMENT '变更前后摘要（已脱敏，绝不含口令/令牌明文）' AFTER `detail`,
  ADD COLUMN `ip` VARCHAR(45) NULL COMMENT '请求来源 IP（IPv4/IPv6）' AFTER `summary`;

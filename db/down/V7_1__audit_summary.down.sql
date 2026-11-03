-- V7_1__audit_summary.down.sql  ·  回退 v1.0 审计摘要列
ALTER TABLE `audit_log`
  DROP COLUMN IF EXISTS `ip`,
  DROP COLUMN IF EXISTS `summary`,
  DROP COLUMN IF EXISTS `operator`;

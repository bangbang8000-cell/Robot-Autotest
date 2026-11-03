-- V6_1 回滚：先摘 run 上的计划字段与索引，再删计划表。
ALTER TABLE `run`
  DROP INDEX `idx_run_plan`,
  DROP COLUMN `triggered_by_plan_at`,
  DROP COLUMN `plan_id`,
  MODIFY COLUMN `trigger_type` VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT 'manual/cron/ci';

DROP TABLE IF EXISTS `execution_plan`;

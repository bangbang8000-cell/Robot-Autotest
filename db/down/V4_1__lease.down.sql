-- V4_1 down：撤销运行字段、租约字段与历史表

DROP TABLE IF EXISTS `resource_lease_history`;

ALTER TABLE `resource_lease`
  DROP INDEX `idx_resource_lease_project`,
  DROP INDEX `idx_resource_lease_expires`,
  DROP COLUMN `release_reason`,
  DROP COLUMN `last_heartbeat_at`,
  DROP COLUMN `project_id`,
  DROP COLUMN `run_id`;

ALTER TABLE `run`
  DROP INDEX `uk_run_project_request`,
  DROP COLUMN `attempt_no`,
  DROP COLUMN `failure_reason`,
  DROP COLUMN `lease_wait_ms`,
  DROP COLUMN `shard_count`,
  DROP COLUMN `workspace_dir`,
  DROP COLUMN `artifacts_dir`,
  DROP COLUMN `failed_tests`,
  DROP COLUMN `passed_tests`,
  DROP COLUMN `total_tests`,
  DROP COLUMN `concurrency`,
  DROP COLUMN `enqueued_at`,
  DROP COLUMN `priority`,
  DROP COLUMN `run_request_id`,
  MODIFY COLUMN `run_status` VARCHAR(32) NOT NULL DEFAULT 'created'
    COMMENT 'created/running/passed/failed/cancelled';

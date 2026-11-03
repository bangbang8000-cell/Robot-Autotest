-- V4_1 执行域：运行状态机字段、幂等键、租约心跳与历史
-- 对应 spec: docs/release/v0.4-execution/spec.md §3.7

ALTER TABLE `run`
  MODIFY COLUMN `run_status` VARCHAR(32) NOT NULL DEFAULT 'queued'
    COMMENT 'queued/preparing/running/finished/failed/canceled',
  ADD COLUMN `run_request_id` VARCHAR(64) NULL COMMENT '幂等键' AFTER `run_id`,
  ADD COLUMN `priority` INT NOT NULL DEFAULT 0 AFTER `trigger_type`,
  ADD COLUMN `enqueued_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER `priority`,
  ADD COLUMN `concurrency` INT NOT NULL DEFAULT 1 AFTER `enqueued_at`,
  ADD COLUMN `total_tests` INT NULL AFTER `finished_at`,
  ADD COLUMN `passed_tests` INT NULL AFTER `total_tests`,
  ADD COLUMN `failed_tests` INT NULL AFTER `passed_tests`,
  ADD COLUMN `artifacts_dir` VARCHAR(512) NULL AFTER `failed_tests`,
  ADD COLUMN `workspace_dir` VARCHAR(512) NULL AFTER `artifacts_dir`,
  ADD COLUMN `shard_count` INT NULL AFTER `workspace_dir`,
  ADD COLUMN `lease_wait_ms` BIGINT NULL AFTER `shard_count`,
  ADD COLUMN `failure_reason` VARCHAR(64) NULL AFTER `lease_wait_ms`,
  ADD COLUMN `attempt_no` INT NOT NULL DEFAULT 0 AFTER `failure_reason`,
  ADD UNIQUE KEY `uk_run_project_request` (`project_id`, `run_request_id`);

ALTER TABLE `resource_lease`
  ADD COLUMN `run_id` VARCHAR(64) NULL AFTER `job_id`,
  ADD COLUMN `project_id` BIGINT NULL AFTER `run_id`,
  ADD COLUMN `last_heartbeat_at` DATETIME(3) NULL AFTER `leased_at`,
  ADD COLUMN `release_reason` VARCHAR(64) NULL AFTER `lease_state`,
  ADD KEY `idx_resource_lease_project` (`project_id`, `lease_state`),
  ADD KEY `idx_resource_lease_expires` (`lease_state`, `expires_at`);

CREATE TABLE `resource_lease_history` (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  resource_key   VARCHAR(128) NOT NULL,
  job_id         BIGINT       NOT NULL,
  run_id         VARCHAR(64)  NOT NULL,
  project_id     BIGINT       NOT NULL,
  acquired_at    DATETIME(3)  NOT NULL,
  released_at    DATETIME(3)  NULL,
  lease_state    VARCHAR(16)  NOT NULL COMMENT 'released/expired/revoked',
  release_reason VARCHAR(64)  NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY `idx_lease_history_key_acquired` (`resource_key`, `acquired_at`),
  KEY `idx_lease_history_run` (`run_id`),
  KEY `idx_lease_history_project` (`project_id`, `lease_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租约历史（冲突判定证据）';

-- V4_2 执行域：运行产物分片与项目归属
-- 对应 spec: docs/release/v0.4-execution/spec.md §3.7

ALTER TABLE `run_artifact`
  ADD COLUMN `project_id` BIGINT NULL AFTER `run_id`,
  ADD COLUMN `shard_no` INT NULL AFTER `kind`,
  ADD COLUMN `line_count` BIGINT NULL AFTER `size_bytes`,
  ADD COLUMN `note` VARCHAR(255) NULL AFTER `availability`,
  ADD KEY `idx_run_artifact_project` (`project_id`, `run_id`),
  ADD KEY `idx_run_artifact_shard` (`run_id`, `shard_no`);

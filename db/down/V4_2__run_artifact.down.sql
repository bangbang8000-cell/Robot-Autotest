-- V4_2 down：撤销产物分片与项目归属

ALTER TABLE `run_artifact`
  DROP INDEX `idx_run_artifact_shard`,
  DROP INDEX `idx_run_artifact_project`,
  DROP COLUMN `note`,
  DROP COLUMN `line_count`,
  DROP COLUMN `shard_no`,
  DROP COLUMN `project_id`;

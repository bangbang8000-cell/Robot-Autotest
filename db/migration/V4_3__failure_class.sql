-- V4_3 执行域：失败分类与运行尝试记录
-- 对应 spec: docs/release/v0.4-execution/spec.md §3.7

ALTER TABLE `run`
  ADD COLUMN `failure_class` VARCHAR(16) NULL AFTER `failure_reason`,
  ADD COLUMN `failure_message` TEXT NULL AFTER `failure_class`,
  ADD COLUMN `retry_of_run_id` VARCHAR(64) NULL AFTER `failure_message`;

CREATE TABLE `run_attempt` (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  project_id    BIGINT      NOT NULL,
  run_id        VARCHAR(64) NOT NULL,
  attempt_no    INT         NOT NULL,
  started_at    DATETIME(3) NULL,
  finished_at   DATETIME(3) NULL,
  verdict       VARCHAR(16) NULL COMMENT 'PASS/FAIL/SKIP/ERROR',
  failure_class VARCHAR(16) NULL,
  failure_msg   TEXT        NULL,
  retried       TINYINT     NOT NULL DEFAULT 0,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY `uk_run_attempt` (`project_id`, `run_id`, `attempt_no`),
  KEY `idx_run_attempt_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行尝试记录（重试可追溯）';

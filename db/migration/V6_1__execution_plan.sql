-- V6_1 执行计划（PLN-01）
-- 口径见 docs/release/v0.6-automation/DEV-PLAN.md §2、§2.4。
--
-- 表名说明：
--   V1__baseline.sql 中「用例集」的实际表名是 `test_set`（`case_set` 在此库中不存在），
--   因此本表外键与 test_set_id 均引用 `test_set`。
--
-- 设计要点：
--   1. 计划状态只允许 enabled / paused；暂停后由调度器跳过，恢复时从「下一次触发点」重算，
--      不补跑暂停期间的历史触发点。
--   2. cron 按 Spring CronExpression 六段式（秒 分 时 日 月 周）解析，不支持年字段。
--   3. next_fire_at 由调度器维护，是「已计算但尚未触发」的时间点；last_fire_at 记录最近一次
--      处理过的触发点（触发、跳过或延迟都会推进），用于判定应用停机期间是否错过触发点。

CREATE TABLE `execution_plan` (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  project_id      BIGINT       NOT NULL,
  name            VARCHAR(128) NOT NULL,
  test_set_id     BIGINT       NULL,
  environment_id  BIGINT       NULL,
  suite_paths_json TEXT        NULL COMMENT '套件路径 JSON 数组（传给 CreateRunCommand.suitePaths）',
  include_tag     VARCHAR(64)  NULL COMMENT 'pabot --include 标签',
  concurrency     INT          NOT NULL DEFAULT 1,
  timeout_seconds INT          NOT NULL DEFAULT 3600,
  cron            VARCHAR(64)  NOT NULL COMMENT 'Spring 六段式：秒 分 时 日 月 周',
  timezone        VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '计划时区；触发计算只用它，不读 systemDefault',
  status          VARCHAR(16)  NOT NULL DEFAULT 'enabled' COMMENT 'enabled/paused',
  overlap_policy  VARCHAR(16)  NOT NULL DEFAULT 'SKIP' COMMENT 'SKIP：上一轮未结束则跳过本次',
  last_fire_at    DATETIME(3)  NULL COMMENT '最近一次处理过的触发点',
  next_fire_at    DATETIME(3)  NULL COMMENT '下一次触发时间（暂停时置空）',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by      BIGINT       NULL,
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by      BIGINT       NULL,
  PRIMARY KEY (id),
  KEY `idx_execution_plan_project_status` (`project_id`, `status`),
  KEY `idx_execution_plan_due` (`status`, `next_fire_at`),
  CONSTRAINT `fk_execution_plan_project` FOREIGN KEY (`project_id`) REFERENCES `project` (id),
  CONSTRAINT `fk_execution_plan_test_set` FOREIGN KEY (`test_set_id`) REFERENCES `test_set` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行计划（v0.6 PLN-01）';

-- run.trigger_type 已由 V1 基线建立（VARCHAR(32)）；这里收窄到 16 并把取值口径写成
-- manual / schedule / ci，保持既有 manual 默认值不变。
ALTER TABLE `run`
  MODIFY COLUMN `trigger_type` VARCHAR(16) NOT NULL DEFAULT 'manual'
    COMMENT 'manual/schedule/ci',
  ADD COLUMN `plan_id` BIGINT NULL COMMENT '触发本次运行的执行计划（手工运行可为空）' AFTER `trigger_type`,
  ADD COLUMN `triggered_by_plan_at` DATETIME(3) NULL
    COMMENT '计划触发点（用于反查本次 run 对应哪一次 cron 时间）' AFTER `plan_id`,
  ADD KEY `idx_run_plan` (`project_id`, `plan_id`);

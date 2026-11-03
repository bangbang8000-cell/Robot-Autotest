-- V6_3 CI 触发与回写（PLN-03）
-- 口径见 docs/release/v0.6-automation/DEV-PLAN.md §4。
--
-- 1) api_token 增加项目绑定与 scope
--    - project_id：项目 API token 必须绑定单个项目；会话令牌保持 NULL。
--    - scopes：逗号分隔的 scope 列表（当前只有 ci:trigger），空串 = 无任何 scope。
--
--    **存量兼容策略（显式写清）**：
--    * V1/V2_1 存量行 token_kind 都是 'session'（V2_1 默认值），本次新增两列全部取
--      默认值（project_id=NULL、scopes=''）后**行为完全不变**：
--      SessionTokenRepository 只按 `token_kind='session' + token_hash` 查询，不读这两列；
--    * 项目 API token 的判定条件是 `token_kind='api' AND project_id IS NOT NULL`，
--      并要求 scopes 含 `ci:trigger`；project_id 为空的 api token 一律 403，不会退化成
--      「可访问所有项目」；
--    * scopes 为空串表示「无 scope」，不是「全部 scope」，不改变既有会话令牌语义。
--
-- 2) ci_trigger：一次 CI 触发一行，(plan_id, commit_sha) 唯一，作为幂等的最终兜底。
--    并发插入冲突时应用层回读唯一键命中的那一行并返回 duplicated=true，不新建 run。

ALTER TABLE `api_token`
  ADD COLUMN `project_id` BIGINT NULL
    COMMENT '项目 API token 绑定的项目；会话令牌为 NULL' AFTER `token_kind`,
  ADD COLUMN `scopes` VARCHAR(128) NOT NULL DEFAULT ''
    COMMENT '逗号分隔 scope，如 ci:trigger；空串=无 scope' AFTER `project_id`,
  ADD KEY `idx_api_token_project_kind` (`project_id`, `token_kind`);

CREATE TABLE `ci_trigger` (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  project_id       BIGINT       NOT NULL,
  plan_id          BIGINT       NOT NULL,
  commit_sha       VARCHAR(64)  NOT NULL COMMENT '触发提交（统一小写，幂等键之一）',
  ref              VARCHAR(255) NULL COMMENT '如 refs/heads/main',
  build            VARCHAR(128) NOT NULL COMMENT 'SW_BUILD 取值；缺省为 sha 前 8 位',
  repo             VARCHAR(255) NULL COMMENT 'owner/repo 或 ssh:// 群晖仓库，用于回写判定',
  run_id           VARCHAR(64)  NULL COMMENT '平台 RUN_ID；建 run 后回填',
  writeback_status VARCHAR(16)  NOT NULL DEFAULT 'pending'
    COMMENT 'pending/success/failed/skipped/not_supported',
  writeback_error  VARCHAR(512) NULL COMMENT '回写失败原因；永不包含令牌',
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by       BIGINT       NULL,
  updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by       BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY `uk_ci_trigger_plan_commit` (`plan_id`, `commit_sha`),
  KEY `idx_ci_trigger_run` (`project_id`, `run_id`),
  KEY `idx_ci_trigger_project_created` (`project_id`, `created_at`),
  CONSTRAINT `fk_ci_trigger_project` FOREIGN KEY (`project_id`) REFERENCES `project` (id),
  CONSTRAINT `fk_ci_trigger_plan` FOREIGN KEY (`plan_id`) REFERENCES `execution_plan` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CI 触发记录（幂等键 + 回写状态）';

-- =============================================================================
-- V2_3__case_fields.sql  ·  v0.2 用例资产业务字段（7 张表）
-- 归属 CAS-01 ~ CAS-05 ｜ 门禁 G2 ｜ 对应 FR-v0.2-16 ~ 24
-- 模型依据 ADR-0005：台账 test_case + 脚本 robot_file.content 唯一真源，不存 AST。
-- =============================================================================

-- 1) 台账：预置条件 / 预期 / 评审状态 / 来源 / 能力维度
ALTER TABLE `test_case`
  ADD COLUMN precondition    TEXT         NULL,
  ADD COLUMN expected        TEXT         NULL,
  ADD COLUMN review_status   VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft/pending/approved/rejected',
  ADD COLUMN review_comment  VARCHAR(512) NULL,
  ADD COLUMN reviewed_by     BIGINT       NULL,
  ADD COLUMN reviewed_at     DATETIME(3)  NULL,
  ADD COLUMN source          VARCHAR(32)  NOT NULL DEFAULT 'import' COMMENT 'import/manual/api',
  ADD COLUMN capability      VARCHAR(64)  NULL COMMENT '能力维度，如 roce-connectivity / pfc-backpressure';

ALTER TABLE `test_case` ADD INDEX idx_test_case_review (project_id, review_status);
ALTER TABLE `test_case` ADD INDEX idx_test_case_priority (project_id, priority);
ALTER TABLE `test_case` ADD INDEX idx_test_case_capability (project_id, capability);

-- 2) 步骤：只补一个来源标记（导入拆行时记录原始行号，便于对账）
ALTER TABLE `test_case_step`
  ADD COLUMN source_row INT NULL COMMENT 'Excel 原始行号，导入对账用';

-- 3) 用例集：负责人与状态
ALTER TABLE `test_set`
  ADD COLUMN owner_id   BIGINT      NULL,
  ADD COLUMN set_status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active/archived';

-- 4) 脚本：当前版本号（版本快照写 robot_file_version）
ALTER TABLE `robot_file`
  ADD COLUMN current_version INT NOT NULL DEFAULT 1;

-- 5) 脚本版本：变更人说明已有 comment，补内容大小
ALTER TABLE `robot_file_version`
  ADD COLUMN content_size INT NULL;

-- 6) 用例追溯：补来源字段
ALTER TABLE `case_requirement`
  ADD COLUMN req_source VARCHAR(32) NULL COMMENT 'import/manual';

-- 7) 用例集条目：补创建来源
ALTER TABLE `test_set_item`
  ADD COLUMN item_source VARCHAR(32) NULL COMMENT 'manual/import';

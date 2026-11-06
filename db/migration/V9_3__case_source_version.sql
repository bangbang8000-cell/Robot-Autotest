-- PUBX-01：用例集变成**可发布资产**，并给它一个可追溯的版本标识。
--
-- 两条口径（改之前先改文档 docs/release/v2.1-platform/PRD.md FR-v2.1-10/11）：
--   ① 版本标识 = 显式版本号 + 内容 hash 双条件。只有 hash 变了才 +1，
--      "打开一次页面" 这类无内容变化的操作不许涨版本（不误报是这条需求的全部价值）。
--   ② **顺序算内容**：用例集的价值就在"按这个顺序跑"，所以 hash 覆盖 seq_no。
--      调整顺序 = 内容变更，必须出新版本。
--
-- 归属沿用 V7_3 的既有模式（owner_scope + project_id 可空 + 生成列做唯一性），
-- 不新造一套 is_platform。

-- -----------------------------------------------------------------------------
-- 1) test_set：加归属范围与版本列
-- -----------------------------------------------------------------------------
ALTER TABLE `test_set`
  MODIFY COLUMN `project_id` BIGINT NULL COMMENT 'PLATFORM 资产为 NULL',
  ADD COLUMN `owner_scope` VARCHAR(16) NOT NULL DEFAULT 'PROJECT'
      COMMENT 'PROJECT 项目私有 / PLATFORM 平台共享',
  ADD COLUMN `source_asset_id` BIGINT NULL
      COMMENT '非空表示本项目这份是平台资产的副本，指向平台 test_set.id';

-- 唯一性辅助列：平台行恒为 0，私有行取 project_id（MySQL 唯一键允许多 NULL，
-- 所以不能直接用可空的 project_id 做唯一键，否则平台资产可以重名）。
ALTER TABLE `test_set`
  ADD COLUMN `scope_project_id` BIGINT
      GENERATED ALWAYS AS (IF(`owner_scope` = 'PLATFORM', 0, `project_id`)) VIRTUAL
      COMMENT '唯一性辅助列（平台=0，私有=project_id）';

-- 外键 fk_test_set_project 原来借用 uk_test_set_project_code 当索引，
-- 直接 DROP 那个唯一键会报 1553（needed in a foreign key constraint）。
-- 先给 project_id 补一个普通索引，外键就有了自己的落脚点。
ALTER TABLE `test_set` ADD KEY `idx_test_set_project` (`project_id`);
ALTER TABLE `test_set` DROP INDEX `uk_test_set_project_code`;
ALTER TABLE `test_set` ADD UNIQUE KEY `uk_test_set_scope_code` (`scope_project_id`, `set_code`);
ALTER TABLE `test_set` ADD KEY `idx_test_set_source` (`source_asset_id`);

-- -----------------------------------------------------------------------------
-- 2) asset_version：每个用例集一行，记版本号 / 内容 hash / 同步状态
--    project_id = 0 是平台资产自己的版本行；副本行的 project_id 是副本所在项目。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `asset_version` (
    `id`                   BIGINT      NOT NULL AUTO_INCREMENT,
    `project_id`           BIGINT      NOT NULL COMMENT '0 = 平台资产',
    `asset_type`           VARCHAR(16) NOT NULL COMMENT 'TESTSET（预留 DEVICE/LINK/CREDENTIAL）',
    `asset_id`             BIGINT      NOT NULL COMMENT 'test_set.id',
    `asset_version`        INT         NOT NULL DEFAULT 1 COMMENT '内容变化才 +1',
    `content_hash`         CHAR(64)    NULL COMMENT '内容规范化后的 SHA-256；NULL = 尚未计算',
    `synced_asset_version` INT         NULL COMMENT '副本已对齐到的平台版本',
    `synced_content_hash`  CHAR(64)    NULL COMMENT '副本对齐时的平台 hash',
    `fork_state`           VARCHAR(16) NOT NULL DEFAULT 'SYNCED' COMMENT 'SYNCED/DIVERGED',
    `source_asset_id`      BIGINT      NULL COMMENT '副本指向的平台 test_set.id',
    `created_at`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by`           BIGINT      NULL,
    `updated_at`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `updated_by`           BIGINT      NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_asset_version` (`project_id`, `asset_type`, `asset_id`),
    KEY `idx_asset_version_source` (`source_asset_id`),
    KEY `idx_asset_version_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产内容版本与副本同步状态（PUBX-01）';

-- -----------------------------------------------------------------------------
-- 3) 存量回填：每个已有用例集一行版本记录
--    content_hash 留 NULL（SQL 里算不出规范化内容 hash），由服务层首次 refresh 补上；
--    NULL 的口径是"未知"，引用方检测不会因此误报"有新版本"。
-- -----------------------------------------------------------------------------
INSERT INTO `asset_version` (`project_id`, `asset_type`, `asset_id`, `asset_version`,
                             `content_hash`, `fork_state`, `source_asset_id`)
SELECT `project_id`, 'TESTSET', `id`, 1, NULL, 'SYNCED', NULL
FROM `test_set`
WHERE `owner_scope` = 'PROJECT' AND `project_id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `asset_version` av
                  WHERE av.project_id = `test_set`.`project_id`
                    AND av.asset_type = 'TESTSET' AND av.asset_id = `test_set`.`id`);

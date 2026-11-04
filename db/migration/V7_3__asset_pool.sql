-- V7_3__asset_pool.sql  ·  v0.2 资产共享池（AST-05 / FR-v0.2-50、52）
-- 归属 AST-05 ｜ 对应 DEV-PLAN §10.8 步骤 1~2、6
--
-- 目标：设备/链路/凭据从「项目私有」升级为「项目私有 或 平台共享」。
--   owner_scope = PROJECT  项目私有（默认，存量全部保持这个值）
--   owner_scope = PLATFORM 平台共享，project_id 为 NULL，由 project_asset 记录哪些项目引用
--
-- 两条不能退让的约束：
--   1. 升级后每个项目看到的资产必须与升级前**完全一致** —— 所以有第 4 段存量回填，
--      每个存量资产按原 project_id 插一条 project_asset，不做「自动转平台」。
--   2. 私有资产转共享只能由用户显式操作（见 POST /api/assets/{type}/{id}/share），
--      schema 里不给任何默认变共享的路径。

-- -----------------------------------------------------------------------------
-- 1) 归属范围列：三表加 owner_scope，project_id 改为可空
-- -----------------------------------------------------------------------------
ALTER TABLE `device`
  ADD COLUMN `owner_scope` VARCHAR(16) NOT NULL DEFAULT 'PROJECT'
      COMMENT 'PROJECT=项目私有 PLATFORM=平台共享' AFTER `project_id`,
  MODIFY COLUMN `project_id` BIGINT NULL COMMENT '项目私有时的归属项目；平台共享后为 NULL';

ALTER TABLE `link`
  ADD COLUMN `owner_scope` VARCHAR(16) NOT NULL DEFAULT 'PROJECT'
      COMMENT 'PROJECT=项目私有 PLATFORM=平台共享' AFTER `project_id`,
  MODIFY COLUMN `project_id` BIGINT NULL COMMENT '项目私有时的归属项目；平台共享后为 NULL';

ALTER TABLE `credential`
  ADD COLUMN `owner_scope` VARCHAR(16) NOT NULL DEFAULT 'PROJECT'
      COMMENT 'PROJECT=项目私有 PLATFORM=平台共享' AFTER `project_id`,
  MODIFY COLUMN `project_id` BIGINT NULL COMMENT '项目私有时的归属项目；平台共享后为 NULL';

-- -----------------------------------------------------------------------------
-- 2) 平台级设备的序列号唯一
--
-- 为什么不直接 UNIQUE(serial_no)：不同项目可以登记同一台借用设备，私有资产按项目隔离；
-- 只有在「平台共享池」里序列号才必须全局唯一（否则同一台机器会被引用成两条资产）。
-- 用生成列把唯一性限定在 owner_scope='PLATFORM' 的行上，私有行恒为 NULL（MySQL 唯一键允许多 NULL）。
-- -----------------------------------------------------------------------------
ALTER TABLE `device`
  ADD COLUMN `platform_serial` VARCHAR(128) GENERATED ALWAYS AS
      (IF(`owner_scope` = 'PLATFORM', `serial_no`, NULL)) VIRTUAL
      COMMENT '仅平台共享设备的序列号，用于全局唯一；私有设备恒为 NULL',
  ADD UNIQUE KEY `uk_device_serial` (`platform_serial`);

-- -----------------------------------------------------------------------------
-- 3) 项目对平台资产的引用
-- -----------------------------------------------------------------------------
CREATE TABLE `project_asset` (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  project_id BIGINT       NOT NULL COMMENT '引用方项目',
  asset_type VARCHAR(16)  NOT NULL COMMENT 'DEVICE/LINK/CREDENTIAL',
  asset_id   BIGINT       NOT NULL COMMENT '资产本体 id（按 asset_type 解释）',
  alias      VARCHAR(128) NULL COMMENT '项目内别名，为空则用资产名',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by BIGINT       NULL,
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY `uk_project_asset` (`project_id`, `asset_type`, `asset_id`),
  KEY `idx_project_asset_asset` (`asset_type`, `asset_id`),
  CONSTRAINT `fk_project_asset_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目对平台资产的引用；删项目自动清理引用，不动物资本体';

-- -----------------------------------------------------------------------------
-- 4) 存量回填（关键：不做这一步，升级后所有项目的资产都会「消失」）
--    存量 owner_scope 一律保持 PROJECT；这里只补「本项目引用自己」的那条记录。
-- -----------------------------------------------------------------------------
INSERT INTO `project_asset` (`project_id`, `asset_type`, `asset_id`, `alias`, `created_by`)
SELECT `project_id`, 'DEVICE', `id`, `name`, NULL FROM `device` WHERE `project_id` IS NOT NULL;

INSERT INTO `project_asset` (`project_id`, `asset_type`, `asset_id`, `alias`, `created_by`)
SELECT `project_id`, 'LINK', `id`, `name`, NULL FROM `link` WHERE `project_id` IS NOT NULL;

INSERT INTO `project_asset` (`project_id`, `asset_type`, `asset_id`, `alias`, `created_by`)
SELECT `project_id`, 'CREDENTIAL', `id`, `name`, NULL FROM `credential` WHERE `project_id` IS NOT NULL;

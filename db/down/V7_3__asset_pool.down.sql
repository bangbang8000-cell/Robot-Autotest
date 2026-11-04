-- V7_3__asset_pool.down.sql  ·  回滚资产共享池的结构
--
-- 回滚顺序：先把「平台共享」的资产按引用记录还回某个项目，再删表/列，最后收紧 project_id。
--
-- 已知限制（设计如此，见 DEV-PLAN §10.8 升级风险与对策）：
--   * 一条平台资产被多个项目引用时，回滚只能还回其中一个（取最小 project_id），
--     这不是数据丢失，但人要在回滚后确认归属；
--   * 若某条平台资产**一条引用都没有**，它的 project_id 无法复原，最后一步
--     「project_id NOT NULL」会直接失败并中止回滚——这是刻意的：宁可回滚失败，
--     也不悄悄把资产塞进任意项目或删掉；
--   * MySQL 的 DDL 会隐式提交，本脚本不是单一事务，失败后需人工按上面的说明收尾。

UPDATE `device` d
   JOIN (SELECT `asset_id`, MIN(`project_id`) AS pid
           FROM `project_asset` WHERE `asset_type` = 'DEVICE' GROUP BY `asset_id`) r
     ON r.`asset_id` = d.`id`
   SET d.`project_id` = r.`pid`
 WHERE d.`project_id` IS NULL;

UPDATE `link` l
   JOIN (SELECT `asset_id`, MIN(`project_id`) AS pid
           FROM `project_asset` WHERE `asset_type` = 'LINK' GROUP BY `asset_id`) r
     ON r.`asset_id` = l.`id`
   SET l.`project_id` = r.`pid`
 WHERE l.`project_id` IS NULL;

UPDATE `credential` c
   JOIN (SELECT `asset_id`, MIN(`project_id`) AS pid
           FROM `project_asset` WHERE `asset_type` = 'CREDENTIAL' GROUP BY `asset_id`) r
     ON r.`asset_id` = c.`id`
   SET c.`project_id` = r.`pid`
 WHERE c.`project_id` IS NULL;

DROP TABLE IF EXISTS `project_asset`;

-- MySQL 收紧列时若列上有外键会报 1832，必须先摘外键再改列、再挂回。
ALTER TABLE `device` DROP FOREIGN KEY `fk_device_project`;
ALTER TABLE `link` DROP FOREIGN KEY `fk_link_project`;
ALTER TABLE `credential` DROP FOREIGN KEY `fk_credential_project`;

ALTER TABLE `device`
  DROP INDEX `uk_device_serial`,
  DROP COLUMN `platform_serial`,
  DROP COLUMN `owner_scope`,
  MODIFY COLUMN `project_id` BIGINT NOT NULL COMMENT '归属项目',
  ADD CONSTRAINT `fk_device_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`);

ALTER TABLE `link`
  DROP COLUMN `owner_scope`,
  MODIFY COLUMN `project_id` BIGINT NOT NULL,
  ADD CONSTRAINT `fk_link_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`);

ALTER TABLE `credential`
  DROP COLUMN `owner_scope`,
  MODIFY COLUMN `project_id` BIGINT NOT NULL,
  ADD CONSTRAINT `fk_credential_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`);

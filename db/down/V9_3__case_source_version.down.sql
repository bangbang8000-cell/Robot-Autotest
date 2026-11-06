-- PUBX-01 回退：删 asset_version，收回 test_set 的平台归属与版本列。
-- 注意：回退会丢内容 hash 与同步状态（都是本版新增的派生数据），用例集内容本身不动。

DROP TABLE IF EXISTS `asset_version`;

ALTER TABLE `test_set` DROP INDEX `idx_test_set_source`;
ALTER TABLE `test_set` DROP INDEX `uk_test_set_scope_code`;
ALTER TABLE `test_set` DROP COLUMN `scope_project_id`;
ALTER TABLE `test_set` DROP COLUMN `source_asset_id`;
ALTER TABLE `test_set` DROP COLUMN `owner_scope`;

UPDATE `test_set` SET `project_id` = 0 WHERE `project_id` IS NULL;
ALTER TABLE `test_set`
  MODIFY COLUMN `project_id` BIGINT NOT NULL;
ALTER TABLE `test_set` ADD UNIQUE KEY `uk_test_set_project_code` (`project_id`, `set_code`);
-- 补的那条普通索引在恢复唯一键之后就是多余的（唯一键已能服务外键）
ALTER TABLE `test_set` DROP INDEX `idx_test_set_project`;

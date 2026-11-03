-- V6_3 回滚：先删表（外键指向 project / execution_plan），再删 api_token 新增列。
DROP TABLE IF EXISTS `ci_trigger`;

ALTER TABLE `api_token` DROP INDEX `idx_api_token_project_kind`;
ALTER TABLE `api_token` DROP COLUMN `scopes`;
ALTER TABLE `api_token` DROP COLUMN `project_id`;

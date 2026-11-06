-- V8_6 回退：先撤两张关联表的代理主键（回到复合主键），再删其余表的审计四列。
-- 注意：若表内已存在 (group_id, user_id) / (project_id, group_id) 重复行，回退会失败 ——
-- 正常业务下唯一键保证不可能出现重复。

ALTER TABLE `test_scenario`
    DROP COLUMN `updated_by`, DROP COLUMN `updated_at`,
    DROP COLUMN `created_by`, DROP COLUMN `created_at`;

ALTER TABLE `project_template`
    DROP COLUMN `updated_by`, DROP COLUMN `updated_at`,
    DROP COLUMN `created_by`, DROP COLUMN `created_at`;

ALTER TABLE `case_source`
    DROP COLUMN `updated_by`, DROP COLUMN `updated_at`,
    DROP COLUMN `created_by`, DROP COLUMN `created_at`;

ALTER TABLE `ai_cli_probe`
    DROP COLUMN `updated_by`, DROP COLUMN `updated_at`,
    DROP COLUMN `created_by`, DROP COLUMN `created_at`;

ALTER TABLE `ai_session`
    DROP COLUMN `updated_by`, DROP COLUMN `updated_at`,
    DROP COLUMN `created_by`, DROP COLUMN `created_at`;

ALTER TABLE `project_group_role`
    DROP COLUMN `updated_by`, DROP COLUMN `updated_at`,
    DROP COLUMN `created_by`, DROP COLUMN `created_at`,
    DROP INDEX `uk_project_group`,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (`project_id`, `group_id`),
    DROP COLUMN `id`;

ALTER TABLE `user_group_member`
    DROP COLUMN `updated_by`, DROP COLUMN `updated_at`,
    DROP COLUMN `created_by`, DROP COLUMN `created_at`,
    DROP INDEX `uk_group_member`,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (`group_id`, `user_id`),
    DROP COLUMN `id`;

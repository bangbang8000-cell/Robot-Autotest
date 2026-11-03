-- V2_1__iam_fields.down.sql  ·  回退 v0.2 身份与项目字段
-- 顺序必须是先删索引再删列（索引依赖列）。
ALTER TABLE `api_token` DROP INDEX idx_api_token_kind_expire;
ALTER TABLE `api_token` DROP COLUMN token_kind;

ALTER TABLE `project_label` DROP COLUMN description;
ALTER TABLE `project_setting` DROP COLUMN description;

ALTER TABLE `project_member` DROP INDEX idx_project_member_user;
ALTER TABLE `project_member` DROP COLUMN joined_at;

ALTER TABLE `project` DROP INDEX idx_project_owner;
ALTER TABLE `project` DROP COLUMN visibility;
ALTER TABLE `project` DROP COLUMN owner_id;

ALTER TABLE `role` DROP INDEX idx_role_built_in;
ALTER TABLE `role` DROP COLUMN permissions;

ALTER TABLE `app_user` DROP INDEX idx_app_user_status;
ALTER TABLE `app_user` DROP INDEX idx_app_user_org;
ALTER TABLE `app_user` DROP COLUMN locked_until;
ALTER TABLE `app_user` DROP COLUMN failed_login_count;
ALTER TABLE `app_user` DROP COLUMN last_password_change_at;
ALTER TABLE `app_user` DROP COLUMN password_expires_at;
ALTER TABLE `app_user` DROP COLUMN org_id;

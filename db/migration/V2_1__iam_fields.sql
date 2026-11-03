-- =============================================================================
-- V2_1__iam_fields.sql  ·  v0.2 身份与项目业务字段（8 张表）
-- 归属 IAM-01 / IAM-02 ｜ 门禁 G2 ｜ 对应 FR-v0.2-01 ~ 08
--
-- 只加业务字段与索引，不动 V1 的历史字节。每处 ALTER 在 V2_1__iam_fields.down.sql
-- 有对称回退（ADR-0003）。
-- =============================================================================

-- 1) 用户：所属组织、密码有效期、失败计数与锁定
ALTER TABLE `app_user`
  ADD COLUMN org_id                 BIGINT      NULL COMMENT '所属组织（本版单 org，多 org 预留）' AFTER status,
  ADD COLUMN password_expires_at    DATETIME(3) NULL COMMENT '密码过期时间，90 天策略' AFTER password_hash,
  ADD COLUMN last_password_change_at DATETIME(3) NULL COMMENT '最近一次改密时间',
  ADD COLUMN failed_login_count     INT         NOT NULL DEFAULT 0 COMMENT '连续失败次数',
  ADD COLUMN locked_until           DATETIME(3)  NULL COMMENT '锁定截止时间（5 次失败锁 15 分钟）';

ALTER TABLE `app_user` ADD INDEX idx_app_user_org (org_id);
ALTER TABLE `app_user` ADD INDEX idx_app_user_status (status);

-- 2) 角色：权限点集合（内置角色由代码定义，自定义角色存 JSON）
ALTER TABLE `role`
  ADD COLUMN permissions TEXT NULL COMMENT '权限点 JSON 数组，如 ["case:read","case:write"]';

ALTER TABLE `role` ADD INDEX idx_role_built_in (built_in);

-- 3) 项目：负责人、可见性
ALTER TABLE `project`
  ADD COLUMN owner_id   BIGINT      NULL COMMENT '项目负责人 user id',
  ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'private' COMMENT 'private/project/public';

ALTER TABLE `project` ADD INDEX idx_project_owner (owner_id);

-- 4) 项目成员：加入时间（角色复用 V1 的 member_role：
--    admin / pm / engineer / viewer，与全局角色四值同名）
ALTER TABLE `project_member`
  ADD COLUMN joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);

ALTER TABLE `project_member` ADD INDEX idx_project_member_user (user_id);

-- 5) 项目配置与标签：补充描述字段，便于管理页展示
ALTER TABLE `project_setting`
  ADD COLUMN description VARCHAR(512) NULL;

ALTER TABLE `project_label`
  ADD COLUMN description VARCHAR(512) NULL;

-- 6) API 令牌：区分会话令牌与长期令牌（会话过期 8 小时）
ALTER TABLE `api_token`
  ADD COLUMN token_kind VARCHAR(16) NOT NULL DEFAULT 'session' COMMENT 'session/api';

ALTER TABLE `api_token` ADD INDEX idx_api_token_kind_expire (token_kind, expires_at);

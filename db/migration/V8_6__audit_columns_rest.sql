-- V8_6 / 基线回归（续）：W4–W6 建表时遗留的审计四列与主键约定。
--
-- 起因（2026-11-06 实测）：`SchemaIT` 在**空库** clean+migrate 后报出 7 张表不合规，
-- 而 `robot_dev` 上的 `db-audit` 只报出 6 张、且是另一批 —— 说明两个机检各漏一半：
--   * `db-audit` 的 SQL 用 `column_name IN (四列)` 后 `HAVING COUNT(*) < 4`，
--     **一列都没有的表根本不进分组**，被完全漏掉（本迁移补的这 7 张正是这种）；
--   * `SchemaIT` 带 `@Tag("it")` 默认跳过，没人跑。
-- 两者都不在门禁里，于是「表建出来了、接口也通了」把约定缺失盖了一个版本。
--
-- 基线口径见 `V1__baseline.sql` 头注释与 `project_member` / `user_role` 两张同类表：
--   每表 = 代理主键 `id` + 审计四列（created_at / created_by / updated_at / updated_by）。
--
-- 本次处理：
--   1) 7 张表补审计四列（纯加列，不动数据；created_at 由 DEFAULT 回填现有行）。
--   2) 2 张纯关联表把复合主键换成代理主键 `id`，原复合键降级为唯一键 ——
--      业务唯一性不变，`INSERT IGNORE` / `ON DUPLICATE KEY UPDATE` 仍命中唯一键。
--      经查 information_schema：这两张表**没有任何外键引用**，换主键不影响其它表。
--   3) ai_session / ai_cli_probe 已有 opened_at / probed_at 这类业务时间零列，
--      保留不动（业务语义要在），另补 created_at 作行创建时间 —— 二者同刻，
--      但审计列是跨表统一约定，查询与导出按约定取列，不逐表猜语义。

-- ---------- 1. 纯关联表：复合主键 → 代理主键 ----------

ALTER TABLE `user_group_member`
    DROP PRIMARY KEY,
    ADD COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT FIRST,
    ADD PRIMARY KEY (`id`),
    ADD UNIQUE KEY `uk_group_member` (`group_id`, `user_id`),
    ADD COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN `created_by` BIGINT NULL,
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    ADD COLUMN `updated_by` BIGINT NULL;

ALTER TABLE `project_group_role`
    DROP PRIMARY KEY,
    ADD COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT FIRST,
    ADD PRIMARY KEY (`id`),
    ADD UNIQUE KEY `uk_project_group` (`project_id`, `group_id`),
    ADD COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN `created_by` BIGINT NULL,
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    ADD COLUMN `updated_by` BIGINT NULL;

-- ---------- 2. 其余 5 张：只补审计四列 ----------

ALTER TABLE `ai_session`
    ADD COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN `created_by` BIGINT NULL,
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    ADD COLUMN `updated_by` BIGINT NULL;

ALTER TABLE `ai_cli_probe`
    ADD COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN `created_by` BIGINT NULL,
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    ADD COLUMN `updated_by` BIGINT NULL;

ALTER TABLE `case_source`
    ADD COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN `created_by` BIGINT NULL,
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    ADD COLUMN `updated_by` BIGINT NULL;

ALTER TABLE `project_template`
    ADD COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN `created_by` BIGINT NULL,
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    ADD COLUMN `updated_by` BIGINT NULL;

ALTER TABLE `test_scenario`
    ADD COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN `created_by` BIGINT NULL,
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    ADD COLUMN `updated_by` BIGINT NULL;

-- V8_5 / 基线回归：补齐 W2–W5 新建表的审计四列（AGENTS.md §「每表必须有审计四列」）。
--
-- 起因（2026-11-06 实测）：`scripts/db-audit.sh` 与 `SchemaIT` 自 V8_1 起就是红的 ——
-- V8_1..V8_4 建表时只写了 created_at（部分带 created_by），未按基线约定补齐
-- created_by / updated_at / updated_by。两个机检都不是默认跑的（db-audit 不在门禁里，
-- SchemaIT 带 @Tag("it") 默认跳过），于是「表建出来了、业务也跑通了」把口径缺失盖住了。
--
-- 分两类处理，不搞一刀切：
--   * 可变业务表（本迁移补列）：test_report（DRAFT→FROZEN→REVOKED/ARCHIVED）、
--     import_batch（PREFLIGHT→导入完成）、user_group（可改名改描述）。
--   * append-only 表（不改表，改为进豁免名单）：test_report_export（每次导出插一行）、
--     test_report_audit（审计日志）、ai_action（AI 动作留痕）。这类表没有「改」这回事，
--     硬塞 updated_at/updated_by 只会逼业务代码写假值，比缺列更糟 —— 与
--     resource_lease_history / run_attempt 同理。

ALTER TABLE `test_report`
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER `created_at`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `updated_at`;

ALTER TABLE `import_batch`
    ADD COLUMN `created_by` BIGINT NULL AFTER `file_count`,
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER `created_by`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `updated_at`;

ALTER TABLE `user_group`
    ADD COLUMN `created_by` BIGINT NULL AFTER `description`,
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER `created_by`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `updated_at`;

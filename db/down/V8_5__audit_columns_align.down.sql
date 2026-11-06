-- V8_5 回退：只删本版补的三张表的审计列，不动表与数据。
ALTER TABLE `user_group`
    DROP COLUMN `updated_by`,
    DROP COLUMN `updated_at`,
    DROP COLUMN `created_by`;

ALTER TABLE `import_batch`
    DROP COLUMN `updated_by`,
    DROP COLUMN `updated_at`,
    DROP COLUMN `created_by`;

ALTER TABLE `test_report`
    DROP COLUMN `updated_by`,
    DROP COLUMN `updated_at`;

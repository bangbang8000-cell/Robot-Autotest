-- V10_1__audit_keyset.down.sql  ·  回退 B5 的两个索引（不删数据、不改列）
ALTER TABLE `test_result` DROP INDEX idx_test_result_case_key;
ALTER TABLE `audit_log` DROP INDEX idx_audit_time_id;

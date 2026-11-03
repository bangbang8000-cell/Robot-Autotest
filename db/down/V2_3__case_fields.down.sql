-- V2_3__case_fields.down.sql  ·  回退 v0.2 用例字段
ALTER TABLE `test_set_item` DROP COLUMN item_source;

ALTER TABLE `case_requirement` DROP COLUMN req_source;

ALTER TABLE `robot_file_version` DROP COLUMN content_size;

ALTER TABLE `robot_file` DROP COLUMN current_version;

ALTER TABLE `test_set` DROP COLUMN set_status;
ALTER TABLE `test_set` DROP COLUMN owner_id;

ALTER TABLE `test_case_step` DROP COLUMN source_row;

ALTER TABLE `test_case` DROP INDEX idx_test_case_capability;
ALTER TABLE `test_case` DROP INDEX idx_test_case_priority;
ALTER TABLE `test_case` DROP INDEX idx_test_case_review;
ALTER TABLE `test_case` DROP COLUMN capability;
ALTER TABLE `test_case` DROP COLUMN source;
ALTER TABLE `test_case` DROP COLUMN reviewed_at;
ALTER TABLE `test_case` DROP COLUMN reviewed_by;
ALTER TABLE `test_case` DROP COLUMN review_comment;
ALTER TABLE `test_case` DROP COLUMN review_status;
ALTER TABLE `test_case` DROP COLUMN expected;
ALTER TABLE `test_case` DROP COLUMN precondition;

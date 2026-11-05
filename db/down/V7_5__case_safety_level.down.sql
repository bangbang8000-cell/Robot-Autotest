-- V7_5 down：撤销用例执行安全级

ALTER TABLE `test_case`
  DROP INDEX idx_test_case_safety;

ALTER TABLE `test_case`
  DROP COLUMN `safety_level`;

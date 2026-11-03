-- V4_3 down：撤销失败分类与运行尝试记录

DROP TABLE IF EXISTS `run_attempt`;

ALTER TABLE `run`
  DROP COLUMN `retry_of_run_id`,
  DROP COLUMN `failure_message`,
  DROP COLUMN `failure_class`;

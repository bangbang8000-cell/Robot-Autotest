-- V5_1 down：逐列撤销报告域字段，脚本可重复执行。
ALTER TABLE `keyword_result`
  DROP COLUMN IF EXISTS `log_span`,
  DROP COLUMN IF EXISTS `arg_count`;

ALTER TABLE `test_result`
  DROP COLUMN IF EXISTS `failure_kind`,
  DROP COLUMN IF EXISTS `log_offset`,
  DROP COLUMN IF EXISTS `case_key`;

ALTER TABLE `run`
  DROP COLUMN IF EXISTS `skip_count`,
  DROP COLUMN IF EXISTS `fail_count`,
  DROP COLUMN IF EXISTS `pass_count`,
  DROP COLUMN IF EXISTS `exportable`,
  DROP COLUMN IF EXISTS `metadata_missing`,
  DROP COLUMN IF EXISTS `metadata_json`,
  DROP COLUMN IF EXISTS `case_kind`,
  DROP COLUMN IF EXISTS `operator`,
  DROP COLUMN IF EXISTS `baseline`,
  DROP COLUMN IF EXISTS `sw_build`,
  DROP COLUMN IF EXISTS `link`,
  DROP COLUMN IF EXISTS `dut`;

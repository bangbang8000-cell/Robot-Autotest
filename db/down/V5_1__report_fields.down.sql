-- V5_1 down：逐列撤销报告域字段，脚本可重复执行。
-- 兼容性：目标库不支持 DROP COLUMN IF EXISTS（2026-11-04 G1 #4 现场复跑 ERROR 1064）
ALTER TABLE `keyword_result`
  DROP COLUMN `log_span`,
  DROP COLUMN `arg_count`;

ALTER TABLE `test_result`
  DROP COLUMN `failure_kind`,
  DROP COLUMN `log_offset`,
  DROP COLUMN `case_key`;

ALTER TABLE `run`
  DROP COLUMN `skip_count`,
  DROP COLUMN `fail_count`,
  DROP COLUMN `pass_count`,
  DROP COLUMN `exportable`,
  DROP COLUMN `metadata_missing`,
  DROP COLUMN `metadata_json`,
  DROP COLUMN `case_kind`,
  DROP COLUMN `operator`,
  DROP COLUMN `baseline`,
  DROP COLUMN `sw_build`,
  DROP COLUMN `link`,
  DROP COLUMN `dut`;

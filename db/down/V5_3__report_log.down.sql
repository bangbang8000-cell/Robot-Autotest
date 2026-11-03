-- V5_3 down：逐列撤销日志定位字段，脚本可重复执行。
ALTER TABLE `keyword_result`
  DROP COLUMN IF EXISTS `log_offset`;

ALTER TABLE `test_result`
  DROP COLUMN IF EXISTS `log_span`;

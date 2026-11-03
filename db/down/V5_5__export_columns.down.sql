-- V5_5 down：逐列撤销导出记录字段，脚本可重复执行。
ALTER TABLE `export_record`
  DROP COLUMN IF EXISTS `size_bytes`,
  DROP COLUMN IF EXISTS `sha256`,
  DROP COLUMN IF EXISTS `file_name`,
  DROP COLUMN IF EXISTS `export_mode`;

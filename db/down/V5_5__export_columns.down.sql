-- V5_5 down：逐列撤销导出记录字段，脚本可重复执行。
-- 兼容性：目标库不支持 DROP COLUMN IF EXISTS（2026-11-04 G1 #4 现场复跑 ERROR 1064）
ALTER TABLE `export_record`
  DROP COLUMN `size_bytes`,
  DROP COLUMN `sha256`,
  DROP COLUMN `file_name`,
  DROP COLUMN `export_mode`;

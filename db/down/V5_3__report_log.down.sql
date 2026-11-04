-- V5_3 down：逐列撤销日志定位字段，脚本可重复执行。
-- 兼容性：目标库不支持 DROP COLUMN IF EXISTS（2026-11-04 G1 #4 现场复跑 ERROR 1064）
ALTER TABLE `keyword_result`
  DROP COLUMN `log_offset`;

ALTER TABLE `test_result`
  DROP COLUMN `log_span`;

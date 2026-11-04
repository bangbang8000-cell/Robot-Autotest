-- V5_4 down：撤销产物保留标记，可重复执行。
-- 兼容性：目标库不支持 DROP COLUMN IF EXISTS（2026-11-04 G1 #4 现场复跑 ERROR 1064）
ALTER TABLE `run_artifact`
  DROP COLUMN `pinned`;

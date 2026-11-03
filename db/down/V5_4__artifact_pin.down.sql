-- V5_4 down：撤销产物保留标记，可重复执行。
ALTER TABLE `run_artifact`
  DROP COLUMN IF EXISTS `pinned`;

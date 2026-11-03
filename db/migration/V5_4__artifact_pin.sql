-- V5_4 产物保留标记（RPT-06）
-- 口径见 docs/release/v0.5-report/DEV-PLAN.md §6：pinned=1 的产物不参与自动清理。
-- availability 新增取值 purged：文件已删除、库记录保留，报告仍可查到该 run。

ALTER TABLE `run_artifact`
  ADD COLUMN `pinned` TINYINT NOT NULL DEFAULT 0 COMMENT '1=手动保留，不参与自动清理' AFTER `note`;

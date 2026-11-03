-- V5_5 报告导出记录字段（RPT-05）
-- 口径见 docs/release/v0.5-report/DEV-PLAN.md §5：
--   导出必须落 export_record，记录格式、模式、文件、校验和与字节数，供历史列表与审计反查。
--   仅补齐字段，不改 V1 已建表结构与既有列语义。

ALTER TABLE `export_record`
  ADD COLUMN `export_mode` VARCHAR(16) NOT NULL DEFAULT 'external'
      COMMENT 'external 外部版 / internal 内部版' AFTER `export_format`,
  ADD COLUMN `file_name` VARCHAR(255) NULL COMMENT '下载文件名' AFTER `file_path`,
  ADD COLUMN `sha256` CHAR(64) NULL COMMENT '产物校验和' AFTER `file_name`,
  ADD COLUMN `size_bytes` BIGINT NULL COMMENT '产物字节数' AFTER `sha256`;

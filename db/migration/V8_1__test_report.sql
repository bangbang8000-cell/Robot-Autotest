-- W2 / RPTX-01：测试报告（对外）与执行报告（对内）分家。
-- 快照自包含：冻结后即使源 run 被删，snapshot_json / snapshot_html 仍可独立渲染。
CREATE TABLE IF NOT EXISTS `test_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `run_id` VARCHAR(64) NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `state` VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    `version` INT NOT NULL DEFAULT 1,
    `snapshot_hash` CHAR(64) DEFAULT NULL,
    `snapshot_json` LONGTEXT,
    `snapshot_html` LONGTEXT,
    `source_summary` TEXT,
    `created_by` BIGINT DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `frozen_at` DATETIME(3) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_run_version` (`project_id`, `run_id`, `version`),
    KEY `idx_report_project_state` (`project_id`, `state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `test_report_export` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `report_id` BIGINT NOT NULL,
    `format` VARCHAR(8) NOT NULL,
    `file_name` VARCHAR(200) NOT NULL,
    `size_bytes` BIGINT NOT NULL DEFAULT 0,
    `watermark` VARCHAR(64) DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_export_report` (`report_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `test_report_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `report_id` BIGINT NOT NULL,
    `action` VARCHAR(32) NOT NULL,
    `from_state` VARCHAR(16) DEFAULT NULL,
    `to_state` VARCHAR(16) DEFAULT NULL,
    `actor_id` BIGINT DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_report_audit` (`report_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

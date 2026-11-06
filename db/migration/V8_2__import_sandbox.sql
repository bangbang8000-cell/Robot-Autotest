-- W4 / IMP-01：导入批次与用例来源。case_source 不带任何设备外键（schema 解耦断言）。
CREATE TABLE IF NOT EXISTS `import_batch` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `source_type` VARCHAR(16) NOT NULL,
    `source_name` VARCHAR(200) NOT NULL,
    `checksum` CHAR(64) NOT NULL,
    `version` INT NOT NULL DEFAULT 1,
    `status` VARCHAR(16) NOT NULL DEFAULT 'PREFLIGHT',
    `file_count` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_checksum` (`project_id`, `checksum`),
    KEY `idx_batch_project` (`project_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `case_source` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `rel_path` VARCHAR(400) NOT NULL,
    `kind` VARCHAR(16) NOT NULL,
    `node_id` VARCHAR(400) NOT NULL,
    `checksum` CHAR(64) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_case_source` (`batch_id`, `node_id`),
    KEY `idx_source_project` (`project_id`, `batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- W6 / AIX：AI 会话、动作审计与 CLI 探测留痕。
CREATE TABLE IF NOT EXISTS `ai_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `opened_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `closed_at` DATETIME(3) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ai_session_project` (`project_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ai_action` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `session_id` BIGINT NOT NULL,
    `action` VARCHAR(32) NOT NULL,
    `level` VARCHAR(8) NOT NULL DEFAULT 'L1',
    `confirmed` TINYINT(1) NOT NULL DEFAULT 0,
    `result` VARCHAR(16) NOT NULL DEFAULT 'DEGRADED',
    `summary` VARCHAR(400) DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_ai_action_session` (`session_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ai_cli_probe` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `cli_name` VARCHAR(32) NOT NULL,
    `available` TINYINT(1) NOT NULL,
    `non_interactive` TINYINT(1) NOT NULL,
    `probed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

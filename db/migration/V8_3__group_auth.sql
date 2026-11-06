-- W5 / IAMX：用户组、组-项目授权、项目模板与一层测试场景（D5：不做 DAG）。
CREATE TABLE IF NOT EXISTS `user_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(400) DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_group_member` (
    `group_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    PRIMARY KEY (`group_id`, `user_id`),
    KEY `idx_member_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `project_group_role` (
    `project_id` BIGINT NOT NULL,
    `group_id` BIGINT NOT NULL,
    `role_code` VARCHAR(16) NOT NULL,
    PRIMARY KEY (`project_id`, `group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `project_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `config_json` TEXT,
    `archived` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `test_scenario` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `name` VARCHAR(150) NOT NULL,
    `case_set_ids` TEXT,
    `parent_scenario_id` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_scenario_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

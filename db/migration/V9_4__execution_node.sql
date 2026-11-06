-- NODX-01：第二执行节点的注册、心跳与状态。
--
-- 口径（FR-v2.1-13/14，改之前先改文档）：
--   ① 令牌**只存 SHA-256 哈希**，明文只在注册响应里出现一次——节点令牌等价于
--      执行凭据，泄库不泄凭据；
--   ② 状态四态：NEW（已注册未心跳）/ ONLINE / OFFLINE（连续 3 个心跳周期缺席，
--      由 NodeHeartbeatMonitor 置位）/ DISABLED（管理员停用）；
--   ③ 节点是**平台级资源**（无 project_id），本表不进 TenantGuard 的项目过滤；
--      接口侧用 isAdmin 收口。
--   ④ run.node_id 顺带在本迁移加列（NODX-02 指定节点执行写这里），避免下个迁移
--      只为一列再开一版。

CREATE TABLE IF NOT EXISTS `execution_node` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT,
    `name`              VARCHAR(64) NOT NULL COMMENT '节点名，唯一',
    `token_hash`        CHAR(64)    NOT NULL COMMENT 'SHA-256(明文令牌)，明文不落库',
    `status`            VARCHAR(16) NOT NULL DEFAULT 'NEW'
                        COMMENT 'NEW / ONLINE / OFFLINE / DISABLED',
    `rf_available`      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '节点上 Robot Framework 可用',
    `pytest_available`  TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '节点上 pytest 可用',
    `max_concurrency`   INT         NOT NULL DEFAULT 1 COMMENT '该节点独立并发上限（NODX-02 计数用）',
    `last_heartbeat_at` DATETIME(3) NULL COMMENT '最近一次心跳；连续 3 周期缺席判离线',
    `created_at`        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        COMMENT '注册时间（审计四列口径，db-audit 检查）',
    `created_by`        BIGINT      NULL COMMENT '注册人',
    `updated_at`        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
    `updated_by`        BIGINT      NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_execution_node_name` (`name`),
    KEY `idx_execution_node_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行节点注册与心跳（NODX-01）';

ALTER TABLE `run`
  ADD COLUMN `node_id` BIGINT NULL COMMENT '非空表示该轮跑在指定执行节点上（NODX-02）',
  ADD KEY `idx_run_node` (`node_id`);

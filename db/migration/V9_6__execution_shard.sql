-- 分布式分片（W2 / FR-v2.2-01..05）：一轮运行按套件切分到多节点执行。
--
-- 口径（改之前先改文档 docs/plan/中期开发功能规划-20261107.md §4 W2）：
--   ① 分片是 run 的**子任务**，不以 MQ 传递——主控与 agent 共用本库，
--      谁拿到分片靠「PENDING 行 + 短租约 + fencing 令牌」的 CAS 抢，避免引消息中间件；
--   ② 租约到期未回写即视为可重抢（LEASE_UNTIL < NOW 且 state = LEASED），
--      重抢时 fencing_token + 1、attempt + 1；旧持有者晚到的回写因令牌对不上被拒；
--   ③ suite_paths 用换行分隔的纯文本（不引 JSON 列），读取侧 split('\n') 即可——
--      分片只是执行边界，不是查询维度；
--   ④ 分片行带 project_id（run 的租户维度），所有读写都要过 TenantGuard 项目过滤；
--   ⑤ 终态四态：DONE / FAILED / LOST；LOST = 租约超时后由主控判失（W3 重派入口）。
--   ⑥ 建表即带上审计四列（db-audit 门禁口径），DATETIME(3)、utf8mb4、单列自增 PK。

CREATE TABLE IF NOT EXISTS `execution_shard` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `run_id`        VARCHAR(64) NOT NULL COMMENT '三方关联键 RUN_ID（与 run.run_id 同值）',
    `project_id`    BIGINT      NOT NULL COMMENT '所属项目（租户过滤列，与 run.project_id 同值）',
    `node_id`       BIGINT      NULL COMMENT '分派/领取到哪台执行节点；NULL = 尚未领取',
    `shard_index`   INT         NOT NULL COMMENT '分片序号，从 0 开始，单轮内唯一',
    `suite_paths`   TEXT        NOT NULL COMMENT '本分片包含的套件路径，换行分隔',
    `state`         VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                    COMMENT 'PENDING / LEASED / DONE / FAILED / LOST',
    `lease_owner`   VARCHAR(64) NULL COMMENT '当前租约持有者（节点名或节点 id 文本）',
    `lease_until`   DATETIME(3) NULL COMMENT '租约到期时间；到期未回写即可被重抢',
    `fencing_token` BIGINT      NOT NULL DEFAULT 0
                    COMMENT 'fencing 令牌：每次领取 +1，旧令牌的回写一律被拒',
    `attempt`       INT         NOT NULL DEFAULT 0 COMMENT '已领取次数（含重派）',
    `test_count`    INT         NOT NULL DEFAULT 0 COMMENT '本分片用例数（回写聚合用）',
    `fail_count`    INT         NOT NULL DEFAULT 0 COMMENT '本分片失败用例数',
    `started_at`    DATETIME(3) NULL COMMENT '首次领取时间',
    `finished_at`   DATETIME(3) NULL COMMENT '进入终态时间',
    `created_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                    COMMENT '创建时间（审计四列口径，db-audit 检查）',
    `created_by`    BIGINT      NULL,
    `updated_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                    ON UPDATE CURRENT_TIMESTAMP(3),
    `updated_by`    BIGINT      NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_shard_run_index` (`run_id`, `shard_index`),
    KEY `idx_shard_state` (`state`, `lease_until`),
    KEY `idx_shard_node` (`node_id`),
    KEY `idx_shard_run` (`run_id`, `project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行分片与租约（W2 分布式执行）';

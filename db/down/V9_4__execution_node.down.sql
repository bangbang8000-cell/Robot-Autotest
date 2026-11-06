-- NODX-01 回退：删 run.node_id，删节点表。
-- 注意：回退会丢节点注册信息（重新注册即可）与 run 的节点归因，运行记录本身不动。

ALTER TABLE `run` DROP KEY `idx_run_node`;
ALTER TABLE `run` DROP COLUMN `node_id`;

DROP TABLE IF EXISTS `execution_node`;

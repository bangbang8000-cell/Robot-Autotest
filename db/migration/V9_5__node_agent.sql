-- NODX-03：执行端 agent 接入。
--
-- 口径（FR-v2.1-13/14，改之前先改文档）：
--   ① agent_base_url 是节点上 agent 的接入地址（如 http://127.0.0.1:8082），
--      主控经 NodeChannel 把任务 POST 到 {agent_base_url}/api/agent/run；
--      NULL = 未部署 agent，指定该节点的 run 在发起侧仍放行（ONLINE 即可），
--      但派发时判 FAILED（node_dispatch_failed），不排队不重投。
--   ② 任务签名密钥 = 节点令牌的 SHA-256（token_hash）：主控存哈希可直接算
--      HMAC-SHA256，节点用自己明文令牌派生同一把 key 验签——不新增密钥存储。
--   ③ 节点与主控同库（本版内网单机口径）：run 状态由节点上的
--      RunExecutionService 正常迁移，output.xml 仍走 HTTP 回传（协议为真实
--      分布式保留，判据「产物完整回传」按回传链路取证）。

ALTER TABLE `execution_node`
  ADD COLUMN `agent_base_url` VARCHAR(200) NULL
  COMMENT '节点 agent 接入地址；NULL = 未部署 agent' AFTER `last_heartbeat_at`;


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `ai_action`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_action` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `action` varchar(32) NOT NULL,
  `level` varchar(8) NOT NULL DEFAULT 'L1',
  `confirmed` tinyint(1) NOT NULL DEFAULT '0',
  `result` varchar(16) NOT NULL DEFAULT 'DEGRADED',
  `summary` varchar(400) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ai_action_session` (`session_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ai_action` WRITE;
/*!40000 ALTER TABLE `ai_action` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_action` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ai_cli_probe`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_cli_probe` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cli_name` varchar(32) NOT NULL,
  `available` tinyint(1) NOT NULL,
  `non_interactive` tinyint(1) NOT NULL,
  `probed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ai_cli_probe` WRITE;
/*!40000 ALTER TABLE `ai_cli_probe` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_cli_probe` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ai_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `opened_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `closed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_session_project` (`project_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ai_session` WRITE;
/*!40000 ALTER TABLE `ai_session` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_session` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `api_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `api_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `token_hash` varchar(100) NOT NULL COMMENT '只存哈希',
  `last_used_at` datetime(3) DEFAULT NULL,
  `expires_at` datetime(3) DEFAULT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `token_kind` varchar(16) NOT NULL DEFAULT 'session' COMMENT 'session/api',
  `project_id` bigint DEFAULT NULL COMMENT '项目 API token 绑定的项目；会话令牌为 NULL',
  `scopes` varchar(128) NOT NULL DEFAULT '' COMMENT '逗号分隔 scope，如 ci:trigger；空串=无 scope',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_token_hash` (`token_hash`),
  KEY `idx_api_token_user` (`user_id`),
  KEY `idx_api_token_kind_expire` (`token_kind`,`expires_at`),
  KEY `idx_api_token_project_kind` (`project_id`,`token_kind`),
  CONSTRAINT `fk_api_token_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='API 令牌';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `api_token` WRITE;
/*!40000 ALTER TABLE `api_token` DISABLE KEYS */;
/*!40000 ALTER TABLE `api_token` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `app_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `display_name` varchar(128) DEFAULT NULL,
  `email` varchar(190) DEFAULT NULL,
  `password_hash` varchar(100) NOT NULL COMMENT '只存哈希，禁止明文',
  `password_expires_at` datetime(3) DEFAULT NULL COMMENT '密码过期时间，90 天策略',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1=启用 0=停用',
  `org_id` bigint DEFAULT NULL COMMENT '所属组织（本版单 org，多 org 预留）',
  `last_login_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `last_password_change_at` datetime(3) DEFAULT NULL COMMENT '最近一次改密时间',
  `failed_login_count` int NOT NULL DEFAULT '0' COMMENT '连续失败次数',
  `locked_until` datetime(3) DEFAULT NULL COMMENT '锁定截止时间（5 次失败锁 15 分钟）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_username` (`username`),
  UNIQUE KEY `uk_app_user_email` (`email`),
  KEY `idx_app_user_org` (`org_id`),
  KEY `idx_app_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台用户';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `app_user` WRITE;
/*!40000 ALTER TABLE `app_user` DISABLE KEYS */;
/*!40000 ALTER TABLE `app_user` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `asset_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '0 = 平台资产',
  `asset_type` varchar(16) NOT NULL COMMENT 'TESTSET（预留 DEVICE/LINK/CREDENTIAL）',
  `asset_id` bigint NOT NULL COMMENT 'test_set.id',
  `asset_version` int NOT NULL DEFAULT '1' COMMENT '内容变化才 +1',
  `content_hash` char(64) DEFAULT NULL COMMENT '内容规范化后的 SHA-256；NULL = 尚未计算',
  `synced_asset_version` int DEFAULT NULL COMMENT '副本已对齐到的平台版本',
  `synced_content_hash` char(64) DEFAULT NULL COMMENT '副本对齐时的平台 hash',
  `fork_state` varchar(16) NOT NULL DEFAULT 'SYNCED' COMMENT 'SYNCED/DIVERGED',
  `source_asset_id` bigint DEFAULT NULL COMMENT '副本指向的平台 test_set.id',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_version` (`project_id`,`asset_type`,`asset_id`),
  KEY `idx_asset_version_source` (`source_asset_id`),
  KEY `idx_asset_version_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产内容版本与副本同步状态（PUBX-01）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `asset_version` WRITE;
/*!40000 ALTER TABLE `asset_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `asset_version` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL COMMENT '弱引用，不加 FK',
  `actor_id` bigint DEFAULT NULL COMMENT '弱引用 app_user，不加 FK',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人用户名快照，避免改名后无法追溯',
  `action` varchar(128) NOT NULL,
  `resource_type` varchar(64) DEFAULT NULL,
  `resource_id` varchar(64) DEFAULT NULL COMMENT '弱引用，不加 FK',
  `detail` text,
  `summary` text COMMENT '变更前后摘要（已脱敏，绝不含口令/令牌明文）',
  `ip` varchar(45) DEFAULT NULL COMMENT '请求来源 IP（IPv4/IPv6）',
  `occurred_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_log_project_time` (`project_id`,`occurred_at`),
  KEY `idx_audit_log_actor_time` (`actor_id`,`occurred_at`),
  KEY `idx_audit_resource` (`resource_type`,`resource_id`),
  KEY `idx_audit_action_time` (`action`,`occurred_at`),
  KEY `idx_audit_project_actor` (`project_id`,`actor_id`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志（v1.0 启用）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `audit_log` WRITE;
/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `case_requirement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_requirement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `test_case_id` bigint NOT NULL,
  `req_key` varchar(64) NOT NULL,
  `req_value` varchar(512) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `req_source` varchar(32) DEFAULT NULL COMMENT 'import/manual',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_case_requirement` (`test_case_id`,`req_key`),
  CONSTRAINT `fk_case_requirement_case` FOREIGN KEY (`test_case_id`) REFERENCES `test_case` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用例前置条件 / 追溯';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `case_requirement` WRITE;
/*!40000 ALTER TABLE `case_requirement` DISABLE KEYS */;
/*!40000 ALTER TABLE `case_requirement` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `case_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_source` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `batch_id` bigint NOT NULL,
  `rel_path` varchar(400) NOT NULL,
  `kind` varchar(16) NOT NULL,
  `node_id` varchar(400) NOT NULL,
  `checksum` char(64) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_case_source` (`batch_id`,`node_id`),
  KEY `idx_source_project` (`project_id`,`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `case_source` WRITE;
/*!40000 ALTER TABLE `case_source` DISABLE KEYS */;
/*!40000 ALTER TABLE `case_source` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ci_trigger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ci_trigger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `plan_id` bigint NOT NULL,
  `commit_sha` varchar(64) NOT NULL COMMENT '触发提交（统一小写，幂等键之一）',
  `ref` varchar(255) DEFAULT NULL COMMENT '如 refs/heads/main',
  `build` varchar(128) NOT NULL COMMENT 'SW_BUILD 取值；缺省为 sha 前 8 位',
  `repo` varchar(255) DEFAULT NULL COMMENT 'owner/repo 或 ssh:// 内网仓库，用于回写判定',
  `run_id` varchar(64) DEFAULT NULL COMMENT '平台 RUN_ID；建 run 后回填',
  `writeback_status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/success/failed/skipped/not_supported',
  `writeback_error` varchar(512) DEFAULT NULL COMMENT '回写失败原因；永不包含令牌',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ci_trigger_plan_commit` (`plan_id`,`commit_sha`),
  KEY `idx_ci_trigger_run` (`project_id`,`run_id`),
  KEY `idx_ci_trigger_project_created` (`project_id`,`created_at`),
  CONSTRAINT `fk_ci_trigger_plan` FOREIGN KEY (`plan_id`) REFERENCES `execution_plan` (`id`),
  CONSTRAINT `fk_ci_trigger_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CI 触发记录（幂等键 + 回写状态）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ci_trigger` WRITE;
/*!40000 ALTER TABLE `ci_trigger` DISABLE KEYS */;
/*!40000 ALTER TABLE `ci_trigger` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `credential`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `credential` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL COMMENT '项目私有时的归属项目；平台共享后为 NULL',
  `owner_scope` varchar(16) NOT NULL DEFAULT 'PROJECT' COMMENT 'PROJECT=项目私有 PLATFORM=平台共享',
  `name` varchar(128) NOT NULL,
  `cred_type` varchar(32) NOT NULL COMMENT 'ssh/token/snmp',
  `username` varchar(128) DEFAULT NULL,
  `secret_ref` varchar(256) NOT NULL COMMENT '指向环境变量或密钥库，禁止存明文口令',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `cipher_blob` text COMMENT 'AES-256-GCM 密文（含 IV），唯一存储形态',
  `key_version` int NOT NULL DEFAULT '1',
  `last_rotated_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_credential_project_name` (`project_id`,`name`),
  CONSTRAINT `fk_credential_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='凭据引用（不存明文）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `credential` WRITE;
/*!40000 ALTER TABLE `credential` DISABLE KEYS */;
/*!40000 ALTER TABLE `credential` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `device`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL COMMENT '项目私有时的归属项目；平台共享后为 NULL',
  `owner_scope` varchar(16) NOT NULL DEFAULT 'PROJECT' COMMENT 'PROJECT=项目私有 PLATFORM=平台共享',
  `name` varchar(128) NOT NULL,
  `device_type` varchar(64) DEFAULT NULL COMMENT 'gpu/server/switch/storage',
  `management_ip` varchar(64) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `model` varchar(128) DEFAULT NULL,
  `vendor` varchar(64) DEFAULT NULL,
  `serial_no` varchar(128) DEFAULT NULL COMMENT '序列号，项目内唯一',
  `location` varchar(128) DEFAULT NULL,
  `rack_no` varchar(32) DEFAULT NULL,
  `domain_tag` varchar(64) DEFAULT NULL COMMENT '技术域标签：rdma/ethernet/qos/virt/offload/gpu',
  `owner_id` bigint DEFAULT NULL,
  `last_checked_at` datetime(3) DEFAULT NULL,
  `platform_serial` varchar(128) GENERATED ALWAYS AS (if((`owner_scope` = _utf8mb4'PLATFORM'),`serial_no`,NULL)) VIRTUAL COMMENT '仅平台共享设备的序列号，用于全局唯一；私有设备恒为 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_project_name` (`project_id`,`name`),
  UNIQUE KEY `uk_device_project_serial` (`project_id`,`serial_no`),
  UNIQUE KEY `uk_device_serial` (`platform_serial`),
  KEY `idx_device_domain` (`project_id`,`domain_tag`),
  CONSTRAINT `fk_device_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `device` WRITE;
/*!40000 ALTER TABLE `device` DISABLE KEYS */;
/*!40000 ALTER TABLE `device` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `device_check`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `device_check` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device_id` bigint NOT NULL,
  `check_type` varchar(32) NOT NULL COMMENT 'ping/ssh/port',
  `check_result` varchar(16) NOT NULL COMMENT 'ok/fail',
  `detail` text,
  `checked_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `latency_ms` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_device_check_device` (`device_id`,`check_type`,`checked_at`),
  CONSTRAINT `fk_device_check_device` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备联通检查记录';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `device_check` WRITE;
/*!40000 ALTER TABLE `device_check` DISABLE KEYS */;
/*!40000 ALTER TABLE `device_check` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `device_port`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `device_port` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device_id` bigint NOT NULL,
  `port_name` varchar(64) NOT NULL,
  `port_type` varchar(32) DEFAULT NULL COMMENT 'eth/ib/mgmt',
  `mac_address` varchar(32) DEFAULT NULL,
  `speed_gbps` int DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `protocol` varchar(32) NOT NULL DEFAULT 'ethernet' COMMENT 'ethernet/rdma/ib/mgmt',
  `port_status` varchar(16) NOT NULL DEFAULT 'up' COMMENT 'up/down/disabled',
  `mtu` int DEFAULT NULL,
  `ip_address` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_port_name` (`device_id`,`port_name`),
  KEY `idx_device_port_protocol` (`device_id`,`protocol`),
  CONSTRAINT `fk_device_port_device` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备端口';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `device_port` WRITE;
/*!40000 ALTER TABLE `device_port` DISABLE KEYS */;
/*!40000 ALTER TABLE `device_port` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `editor_keyword`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `editor_keyword` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL DEFAULT '0' COMMENT '0=平台级（库/BuiltIn），>0=项目私有',
  `library` varchar(128) NOT NULL COMMENT '来源库或分类名',
  `name` varchar(256) NOT NULL COMMENT '关键词名（RF 大小写不敏感，查询时按库排序）',
  `args_json` varchar(1024) DEFAULT NULL COMMENT '参数名 JSON 数组，如 ["actual","expected","msg=None"]',
  `doc` text COMMENT '关键词文档（纯文本，面板内联展示）',
  `source` varchar(16) NOT NULL DEFAULT 'library' COMMENT 'builtin/library/project',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_editor_keyword_scope` (`project_id`,`library`,`name`),
  KEY `idx_editor_keyword_name` (`project_id`,`name`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='编辑器关键词元数据（平台级 + 项目私有）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `editor_keyword` WRITE;
/*!40000 ALTER TABLE `editor_keyword` DISABLE KEYS */;
INSERT INTO `editor_keyword` VALUES (1,0,'BuiltIn','Should Be Equal','[\"actual\",\"expected\",\"msg=None\",\"values=True\",\"formatter=str\"]','断言两个值相等；失败时输出 msg。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(2,0,'BuiltIn','Should Contain','[\"container\",\"item\",\"msg=None\",\"values=True\"]','断言 container 包含 item（字符串或列表）。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(3,0,'BuiltIn','Should Be True','[\"condition\",\"msg=None\"]','断言 condition 为真。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(4,0,'BuiltIn','Should Not Be Empty','[\"item\",\"msg=None\"]','断言值非空。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(5,0,'BuiltIn','Log','[\"message\",\"level=INFO\",\"html=False\",\"console=False\",\"repr=False\"]','写一条日志到 report.html。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(6,0,'BuiltIn','Log To Console','[\"message\",\"stream=STDOUT\",\"no_newline=False\"]','写一条日志到 stdout（平台看门狗只认这个）。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(7,0,'BuiltIn','Sleep','[\"time\",\"reason=None\"]','等待指定时间，格式如 1s / 500ms。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(8,0,'BuiltIn','Set Variable','[\"*values\"]','把参数原样返回，用于赋值。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(9,0,'BuiltIn','Set Test Variable','[\"name\",\"*values\"]','设置测试级变量。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(10,0,'BuiltIn','Set Suite Variable','[\"name\",\"*values\"]','设置套件级变量。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(11,0,'BuiltIn','Run Keyword If','[\"condition\",\"name\",\"*args\"]','条件成立时执行关键字。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(12,0,'BuiltIn','Run Keyword And Return Status','[\"name\",\"*args\"]','执行关键字并返回布尔状态，不抛失败。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(13,0,'BuiltIn','Evaluate','[\"expression\",\"modules=None\",\"namespace=None\"]','执行 Python 表达式并返回结果。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(14,0,'BuiltIn','Catenate','[\"separator=SPACE\",\"*items\"]','拼接多个值。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(15,0,'BuiltIn','Get Length','[\"item\"]','返回字符串/列表/字典的长度。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(16,0,'BuiltIn','Convert To Integer','[\"item\",\"base=None\"]','转换为整数。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(17,0,'BuiltIn','Convert To String','[\"item\"]','转换为字符串。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(18,0,'BuiltIn','Fail','[\"msg=None\",\"*tags\"]','主动让用例失败。','builtin','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(19,0,'Collections','Create List','[\"*items\"]','创建列表。','library','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(20,0,'Collections','Create Dictionary','[\"*items\"]','创建字典。','library','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(21,0,'Collections','Append To List','[\"list_\",\"*values\"]','向列表追加元素。','library','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(22,0,'Collections','Get From Dictionary','[\"dictionary\",\"key\"]','按键取字典值。','library','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(23,0,'Collections','List Should Contain Value','[\"list_\",\"value\",\"msg=None\"]','断言列表包含某值。','library','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(24,0,'String','Replace String','[\"string\",\"search_for\",\"replace_with\",\"count=-1\"]','字符串替换。','library','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(25,0,'String','Split String','[\"string\",\"separator=None\",\"max_split=-1\"]','按分隔符拆分字符串。','library','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(26,0,'String','Convert To Upper Case','[\"string\"]','转换为大写。','library','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(27,0,'OperatingSystem','Create File','[\"path\",\"content=EMPTY\",\"encoding=UTF-8\"]','创建文件。','library','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(28,0,'OperatingSystem','Remove File','[\"path\"]','删除文件。','library','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL),(29,0,'OperatingSystem','File Should Exist','[\"path\",\"msg=None\"]','断言文件存在。','library','2026-11-07 11:03:41.899',NULL,'2026-11-07 11:03:41.899',NULL);
/*!40000 ALTER TABLE `editor_keyword` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `env_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `env_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `profile_type` varchar(64) DEFAULT NULL COMMENT 'os/ofed/driver 基线画像',
  `description` varchar(512) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `snapshot_json` longtext COMMENT '拓扑与关键参数快照 JSON',
  `snapshot_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_env_profile_project_name` (`project_id`,`name`),
  CONSTRAINT `fk_env_profile_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='环境画像';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `env_profile` WRITE;
/*!40000 ALTER TABLE `env_profile` DISABLE KEYS */;
/*!40000 ALTER TABLE `env_profile` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `execution_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `execution_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint DEFAULT NULL,
  `run_key` varchar(64) DEFAULT NULL COMMENT '弱引用 run.run_id，不加 FK',
  `event_type` varchar(64) NOT NULL,
  `payload` text,
  `occurred_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_execution_event_job` (`job_id`,`occurred_at`),
  KEY `idx_execution_event_run` (`run_key`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='执行事件流';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `execution_event` WRITE;
/*!40000 ALTER TABLE `execution_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `execution_event` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `execution_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `execution_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `job_no` varchar(64) NOT NULL,
  `job_status` varchar(32) NOT NULL DEFAULT 'submitted' COMMENT 'submitted/queued/running/finished/cancelled',
  `submitted_by` bigint DEFAULT NULL,
  `submitted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_job_no` (`job_no`),
  KEY `idx_execution_job_project` (`project_id`,`job_status`),
  CONSTRAINT `fk_execution_job_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='执行提交（v0.4 补字段）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `execution_job` WRITE;
/*!40000 ALTER TABLE `execution_job` DISABLE KEYS */;
/*!40000 ALTER TABLE `execution_job` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `execution_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `execution_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '节点名，唯一',
  `token_hash` char(64) NOT NULL COMMENT 'SHA-256(明文令牌)，明文不落库',
  `status` varchar(16) NOT NULL DEFAULT 'NEW' COMMENT 'NEW / ONLINE / OFFLINE / DISABLED',
  `rf_available` tinyint(1) NOT NULL DEFAULT '0' COMMENT '节点上 Robot Framework 可用',
  `pytest_available` tinyint(1) NOT NULL DEFAULT '0' COMMENT '节点上 pytest 可用',
  `max_concurrency` int NOT NULL DEFAULT '1' COMMENT '该节点独立并发上限（NODX-02 计数用）',
  `last_heartbeat_at` datetime(3) DEFAULT NULL COMMENT '最近一次心跳；连续 3 周期缺席判离线',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '注册时间（审计四列口径，db-audit 检查）',
  `created_by` bigint DEFAULT NULL COMMENT '注册人',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_node_name` (`name`),
  KEY `idx_execution_node_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='执行节点注册与心跳（NODX-01）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `execution_node` WRITE;
/*!40000 ALTER TABLE `execution_node` DISABLE KEYS */;
/*!40000 ALTER TABLE `execution_node` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `execution_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `execution_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `test_set_id` bigint DEFAULT NULL,
  `environment_id` bigint DEFAULT NULL,
  `suite_paths_json` text COMMENT '套件路径 JSON 数组（传给 CreateRunCommand.suitePaths）',
  `include_tag` varchar(64) DEFAULT NULL COMMENT 'pabot --include 标签',
  `concurrency` int NOT NULL DEFAULT '1',
  `timeout_seconds` int NOT NULL DEFAULT '3600',
  `cron` varchar(64) NOT NULL COMMENT 'Spring 六段式：秒 分 时 日 月 周',
  `timezone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '计划时区；触发计算只用它，不读 systemDefault',
  `status` varchar(16) NOT NULL DEFAULT 'enabled' COMMENT 'enabled/paused',
  `overlap_policy` varchar(16) NOT NULL DEFAULT 'SKIP' COMMENT 'SKIP：上一轮未结束则跳过本次',
  `last_fire_at` datetime(3) DEFAULT NULL COMMENT '最近一次处理过的触发点',
  `next_fire_at` datetime(3) DEFAULT NULL COMMENT '下一次触发时间（暂停时置空）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_execution_plan_project_status` (`project_id`,`status`),
  KEY `idx_execution_plan_due` (`status`,`next_fire_at`),
  KEY `fk_execution_plan_test_set` (`test_set_id`),
  CONSTRAINT `fk_execution_plan_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_execution_plan_test_set` FOREIGN KEY (`test_set_id`) REFERENCES `test_set` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='执行计划（v0.6 PLN-01）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `execution_plan` WRITE;
/*!40000 ALTER TABLE `execution_plan` DISABLE KEYS */;
/*!40000 ALTER TABLE `execution_plan` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `execution_plan_dependency`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `execution_plan_dependency` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `plan_id` bigint NOT NULL COMMENT '后置计划',
  `depends_on_plan_id` bigint NOT NULL COMMENT '前置计划',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_plan_dependency` (`plan_id`,`depends_on_plan_id`),
  KEY `idx_execution_plan_dependency_project` (`project_id`,`plan_id`),
  KEY `idx_execution_plan_dependency_upstream` (`project_id`,`depends_on_plan_id`),
  KEY `fk_execution_plan_dependency_upstream` (`depends_on_plan_id`),
  CONSTRAINT `fk_execution_plan_dependency_plan` FOREIGN KEY (`plan_id`) REFERENCES `execution_plan` (`id`),
  CONSTRAINT `fk_execution_plan_dependency_upstream` FOREIGN KEY (`depends_on_plan_id`) REFERENCES `execution_plan` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划依赖（前置 -> 后置 DAG 边）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `execution_plan_dependency` WRITE;
/*!40000 ALTER TABLE `execution_plan_dependency` DISABLE KEYS */;
/*!40000 ALTER TABLE `execution_plan_dependency` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `execution_queue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `execution_queue` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint NOT NULL,
  `priority` int NOT NULL DEFAULT '0',
  `queue_state` varchar(16) NOT NULL DEFAULT 'waiting' COMMENT 'waiting/running/done',
  `enqueued_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `started_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_queue_job` (`job_id`),
  KEY `idx_execution_queue_state` (`queue_state`,`priority`),
  CONSTRAINT `fk_execution_queue_job` FOREIGN KEY (`job_id`) REFERENCES `execution_job` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='执行队列（v0.4 补字段）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `execution_queue` WRITE;
/*!40000 ALTER TABLE `execution_queue` DISABLE KEYS */;
/*!40000 ALTER TABLE `execution_queue` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `export_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `export_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `run_key` varchar(64) DEFAULT NULL COMMENT '弱引用 RUN_ID',
  `export_format` varchar(16) NOT NULL COMMENT 'pdf/docx/xlsx',
  `export_mode` varchar(16) NOT NULL DEFAULT 'external' COMMENT 'external 外部版 / internal 内部版',
  `file_path` varchar(512) DEFAULT NULL,
  `file_name` varchar(255) DEFAULT NULL COMMENT '下载文件名',
  `sha256` char(64) DEFAULT NULL COMMENT '产物校验和',
  `size_bytes` bigint DEFAULT NULL COMMENT '产物字节数',
  `export_status` varchar(16) NOT NULL DEFAULT 'created' COMMENT 'created/failed/expired',
  `requested_by` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_export_record_run` (`run_key`),
  KEY `fk_export_record_project` (`project_id`),
  CONSTRAINT `fk_export_record_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导出记录（v0.5 启用）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `export_record` WRITE;
/*!40000 ALTER TABLE `export_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `export_record` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_general_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','baseline','SQL','V1__baseline.sql',-668286497,'robot','2026-11-07 03:03:40',1449,1),(2,'2.1','iam fields','SQL','V2_1__iam_fields.sql',2121461503,'robot','2026-11-07 03:03:40',675,1),(3,'2.2','asset fields','SQL','V2_2__asset_fields.sql',-942728913,'robot','2026-11-07 03:03:41',469,1),(4,'2.3','case fields','SQL','V2_3__case_fields.sql',-601028789,'robot','2026-11-07 03:03:41',481,1),(5,'2.4','audit indexes','SQL','V2_4__audit_indexes.sql',-1455733836,'robot','2026-11-07 03:03:41',62,1),(6,'2.5','editor keywords','SQL','V2_5__editor_keywords.sql',80717174,'robot','2026-11-07 03:03:41',44,1),(7,'4.1','lease','SQL','V4_1__lease.sql',2115073280,'robot','2026-11-07 03:03:42',179,1),(8,'4.2','run artifact','SQL','V4_2__run_artifact.sql',1425237817,'robot','2026-11-07 03:03:42',73,1),(9,'4.3','failure class','SQL','V4_3__failure_class.sql',-309047078,'robot','2026-11-07 03:03:42',110,1),(10,'5.1','report fields','SQL','V5_1__report_fields.sql',1534323982,'robot','2026-11-07 03:03:42',209,1),(11,'5.3','report log','SQL','V5_3__report_log.sql',-629788235,'robot','2026-11-07 03:03:42',116,1),(12,'5.4','artifact pin','SQL','V5_4__artifact_pin.sql',553337793,'robot','2026-11-07 03:03:42',61,1),(13,'5.5','export columns','SQL','V5_5__export_columns.sql',-1633430820,'robot','2026-11-07 03:03:42',61,1),(14,'6.1','execution plan','SQL','V6_1__execution_plan.sql',2007394684,'robot','2026-11-07 03:03:43',152,1),(15,'6.2','plan dependency','SQL','V6_2__plan_dependency.sql',1831555729,'robot','2026-11-07 03:03:43',67,1),(16,'6.3','ci trigger','SQL','V6_3__ci_trigger.sql',1511966559,'robot','2026-11-07 03:03:43',125,1),(17,'7.1','audit summary','SQL','V7_1__audit_summary.sql',809184076,'robot','2026-11-07 03:03:43',100,1),(18,'7.2','user preference','SQL','V7_2__user_preference.sql',454670854,'robot','2026-11-07 03:03:43',39,1),(19,'7.3','asset pool','SQL','V7_3__asset_pool.sql',-208424959,'robot','2026-11-07 03:03:43',386,1),(20,'7.4','run device scope','SQL','V7_4__run_device_scope.sql',-1076751466,'robot','2026-11-07 03:03:44',89,1),(21,'7.5','case safety level','SQL','V7_5__case_safety_level.sql',1351550485,'robot','2026-11-07 03:03:44',106,1),(22,'7.6','run metadata','SQL','V7_6__run_metadata.sql',1943176925,'robot','2026-11-07 03:03:44',38,1),(23,'7.7','project owner backfill','SQL','V7_7__project_owner_backfill.sql',-1554565144,'robot','2026-11-07 03:03:44',3,1),(24,'7.8','domain tag backfill','SQL','V7_8__domain_tag_backfill.sql',1772849824,'robot','2026-11-07 03:03:44',3,1),(25,'8.1','test report','SQL','V8_1__test_report.sql',-1937627191,'robot','2026-11-07 03:03:44',102,1),(26,'8.2','import sandbox','SQL','V8_2__import_sandbox.sql',-2011215194,'robot','2026-11-07 03:03:44',76,1),(27,'8.3','group auth','SQL','V8_3__group_auth.sql',-1512946066,'robot','2026-11-07 03:03:44',153,1),(28,'8.4','ai panel','SQL','V8_4__ai_panel.sql',1469883363,'robot','2026-11-07 03:03:44',97,1),(29,'8.5','audit columns align','SQL','V8_5__audit_columns_align.sql',-801689945,'robot','2026-11-07 03:03:45',196,1),(30,'8.6','audit columns rest','SQL','V8_6__audit_columns_rest.sql',493276134,'robot','2026-11-07 03:03:45',382,1),(31,'9.1','plan group','SQL','V9_1__plan_group.sql',-1353696425,'robot','2026-11-07 03:03:45',86,1),(32,'9.2','report template','SQL','V9_2__report_template.sql',930823428,'robot','2026-11-07 03:03:45',97,1),(33,'9.3','case source version','SQL','V9_3__case_source_version.sql',-884895515,'robot','2026-11-07 03:03:46',296,1),(34,'9.4','execution node','SQL','V9_4__execution_node.sql',709746328,'robot','2026-11-07 03:03:46',137,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `import_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `import_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `source_type` varchar(16) NOT NULL,
  `source_name` varchar(200) NOT NULL,
  `checksum` char(64) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `status` varchar(16) NOT NULL DEFAULT 'PREFLIGHT',
  `file_count` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_checksum` (`project_id`,`checksum`),
  KEY `idx_batch_project` (`project_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `import_batch` WRITE;
/*!40000 ALTER TABLE `import_batch` DISABLE KEYS */;
/*!40000 ALTER TABLE `import_batch` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `keyword_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `keyword_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` bigint NOT NULL,
  `test_result_id` bigint DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `kw_name` varchar(256) NOT NULL,
  `kw_status` varchar(16) DEFAULT NULL,
  `depth` int NOT NULL DEFAULT '0',
  `arg_count` smallint NOT NULL DEFAULT '0' COMMENT '关键字参数个数',
  `log_offset` bigint DEFAULT NULL COMMENT '关键字首条消息在 output.xml 中的序号',
  `log_span` varchar(64) DEFAULT NULL COMMENT '日志行区间 start:end，日志不重复存',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_keyword_result_run` (`run_id`),
  KEY `idx_keyword_result_test_result` (`test_result_id`),
  CONSTRAINT `fk_keyword_result_run` FOREIGN KEY (`run_id`) REFERENCES `run` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关键字结果树（v0.5 补字段）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `keyword_result` WRITE;
/*!40000 ALTER TABLE `keyword_result` DISABLE KEYS */;
/*!40000 ALTER TABLE `keyword_result` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `link`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `link` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL COMMENT '项目私有时的归属项目；平台共享后为 NULL',
  `owner_scope` varchar(16) NOT NULL DEFAULT 'PROJECT' COMMENT 'PROJECT=项目私有 PLATFORM=平台共享',
  `name` varchar(128) NOT NULL,
  `link_type` varchar(32) DEFAULT NULL COMMENT 'rdma/eth/ib',
  `a_device_id` bigint DEFAULT NULL,
  `a_port_id` bigint DEFAULT NULL,
  `b_device_id` bigint DEFAULT NULL,
  `b_port_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `speed_gbps` int DEFAULT NULL,
  `mtu` int DEFAULT NULL,
  `protocol` varchar(32) DEFAULT NULL COMMENT 'ethernet/rdma/ib',
  `description` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_link_project_name` (`project_id`,`name`),
  KEY `idx_link_a_port` (`a_port_id`),
  KEY `idx_link_b_port` (`b_port_id`),
  CONSTRAINT `fk_link_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链路（两端都记）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `link` WRITE;
/*!40000 ALTER TABLE `link` DISABLE KEYS */;
/*!40000 ALTER TABLE `link` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL,
  `run_key` varchar(64) DEFAULT NULL COMMENT '弱引用 RUN_ID',
  `channel` varchar(32) NOT NULL DEFAULT 'web',
  `notification_type` varchar(32) NOT NULL,
  `notify_status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/sent/failed',
  `message` varchar(512) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_dedup` (`run_key`,`channel`,`notification_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知（按 run+channel+type 去重）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `notification` WRITE;
/*!40000 ALTER TABLE `notification` DISABLE KEYS */;
/*!40000 ALTER TABLE `notification` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `plan_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plan_group` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_group_name` (`project_id`,`name`),
  KEY `idx_plan_group_project` (`project_id`),
  CONSTRAINT `fk_plan_group_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划组（v2.1 ORCH-01）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `plan_group` WRITE;
/*!40000 ALTER TABLE `plan_group` DISABLE KEYS */;
INSERT INTO `plan_group` VALUES (1,9001,'TC32 计划组','往返夹具，down V9_1 后应随之消失','2026-11-07 11:03:46.714',NULL,'2026-11-07 11:03:46.714',NULL);
/*!40000 ALTER TABLE `plan_group` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `plan_group_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plan_group_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `plan_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '链式顺序，升序；相邻两条建成一条依赖边',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_group_item` (`group_id`,`plan_id`),
  KEY `idx_plan_group_item_order` (`group_id`,`sort_order`),
  KEY `idx_plan_group_item_project` (`project_id`,`plan_id`),
  KEY `fk_plan_group_item_plan` (`plan_id`),
  CONSTRAINT `fk_plan_group_item_group` FOREIGN KEY (`group_id`) REFERENCES `plan_group` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_plan_group_item_plan` FOREIGN KEY (`plan_id`) REFERENCES `execution_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划组成员（组内顺序）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `plan_group_item` WRITE;
/*!40000 ALTER TABLE `plan_group_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `plan_group_item` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL COMMENT '项目代号，对外引用用这个',
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1=启用 0=停用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `owner_id` bigint DEFAULT NULL COMMENT '项目负责人 user id',
  `visibility` varchar(16) NOT NULL DEFAULT 'private' COMMENT 'private/project/public',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`code`),
  KEY `idx_project_owner` (`owner_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `project` WRITE;
/*!40000 ALTER TABLE `project` DISABLE KEYS */;
INSERT INTO `project` VALUES (9001,'TC32-ROUNDTRIP','TC32 往返夹具',NULL,1,'2026-11-07 11:03:46.698',NULL,'2026-11-07 11:03:46.698',NULL,NULL,'private');
/*!40000 ALTER TABLE `project` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `project_asset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_asset` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '引用方项目',
  `asset_type` varchar(16) NOT NULL COMMENT 'DEVICE/LINK/CREDENTIAL',
  `asset_id` bigint NOT NULL COMMENT '资产本体 id（按 asset_type 解释）',
  `alias` varchar(128) DEFAULT NULL COMMENT '项目内别名，为空则用资产名',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_asset` (`project_id`,`asset_type`,`asset_id`),
  KEY `idx_project_asset_asset` (`asset_type`,`asset_id`),
  CONSTRAINT `fk_project_asset_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目对平台资产的引用；删项目自动清理引用，不动物资本体';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `project_asset` WRITE;
/*!40000 ALTER TABLE `project_asset` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_asset` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `project_group_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_group_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `role_code` varchar(16) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_group` (`project_id`,`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `project_group_role` WRITE;
/*!40000 ALTER TABLE `project_group_role` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_group_role` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `project_label`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_label` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `label_key` varchar(64) NOT NULL,
  `label_value` varchar(128) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `description` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_label` (`project_id`,`label_key`,`label_value`),
  CONSTRAINT `fk_project_label_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目标签（技术域等）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `project_label` WRITE;
/*!40000 ALTER TABLE `project_label` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_label` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `project_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `member_role` varchar(32) NOT NULL DEFAULT 'member' COMMENT 'owner/member/viewer',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `joined_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_member` (`project_id`,`user_id`),
  KEY `idx_project_member_user` (`user_id`),
  CONSTRAINT `fk_project_member_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_project_member_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目成员（行级隔离的依据）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `project_member` WRITE;
/*!40000 ALTER TABLE `project_member` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_member` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `project_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `setting_key` varchar(128) NOT NULL,
  `setting_value` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `description` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_setting_key` (`project_id`,`setting_key`),
  CONSTRAINT `fk_project_setting_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目级配置项';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `project_setting` WRITE;
/*!40000 ALTER TABLE `project_setting` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_setting` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `project_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `config_json` text,
  `archived` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `project_template` WRITE;
/*!40000 ALTER TABLE `project_template` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_template` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `report_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL DEFAULT '0' COMMENT '0=平台级（内置只读），>0=项目私有',
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '1' COMMENT '每次更新自增，供报告快照回指',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '项目默认模板；平台级恒为 1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_template_name` (`project_id`,`name`),
  KEY `idx_report_template_project` (`project_id`),
  KEY `idx_report_template_default` (`project_id`,`is_default`),
  CONSTRAINT `ck_report_template_version` CHECK ((`version` >= 1))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报告模板（v2.1 TPLX-01）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `report_template` WRITE;
/*!40000 ALTER TABLE `report_template` DISABLE KEYS */;
INSERT INTO `report_template` VALUES (1,0,'DEFAULT','平台内置：C5 白名单全部字段，默认顺序',1,1,'2026-11-07 11:03:45.674',NULL,'2026-11-07 11:03:45.674',NULL);
/*!40000 ALTER TABLE `report_template` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `report_template_field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_template_field` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL DEFAULT '0' COMMENT '与所属模板同 scope，便于租户条件直查',
  `template_id` bigint NOT NULL,
  `field_key` varchar(64) NOT NULL COMMENT '取 C5 白名单 key，如 passRate',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '升序即导出顺序',
  `title_override` varchar(128) DEFAULT NULL COMMENT '自定义列名；空则用默认标题',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_template_field` (`template_id`,`field_key`),
  KEY `idx_report_template_field_order` (`template_id`,`sort_order`),
  KEY `idx_report_template_field_project` (`project_id`),
  CONSTRAINT `fk_report_template_field_template` FOREIGN KEY (`template_id`) REFERENCES `report_template` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报告模板字段（顺序 + 自定义标题）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `report_template_field` WRITE;
/*!40000 ALTER TABLE `report_template_field` DISABLE KEYS */;
INSERT INTO `report_template_field` VALUES (1,0,1,'reportId',0,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(2,0,1,'title',1,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(3,0,1,'version',2,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(4,0,1,'frozenAt',3,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(5,0,1,'conclusion',4,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(6,0,1,'passCount',5,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(7,0,1,'failCount',6,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(8,0,1,'skipCount',7,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(9,0,1,'passRate',8,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(10,0,1,'caseTotal',9,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(11,0,1,'caseCovered',10,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(12,0,1,'coverageRate',11,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(13,0,1,'failureDistribution',12,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(14,0,1,'trend',13,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(15,0,1,'projectName',14,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(16,0,1,'swBuild',15,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(17,0,1,'caseKind',16,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(18,0,1,'startedAt',17,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(19,0,1,'finishedAt',18,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(20,0,1,'disclaimer',19,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL),(21,0,1,'watermark',20,NULL,'2026-11-07 11:03:45.678',NULL,'2026-11-07 11:03:45.678',NULL);
/*!40000 ALTER TABLE `report_template_field` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `resource_lease`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `resource_lease` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint NOT NULL,
  `run_id` varchar(64) DEFAULT NULL,
  `project_id` bigint DEFAULT NULL,
  `resource_key` varchar(128) NOT NULL COMMENT '设备#端口粒度，业务主键；唯一即互斥',
  `lease_state` varchar(16) NOT NULL DEFAULT 'held' COMMENT 'held/released/expired',
  `release_reason` varchar(64) DEFAULT NULL,
  `leased_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `last_heartbeat_at` datetime(3) DEFAULT NULL,
  `expires_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_lease_key` (`resource_key`),
  KEY `idx_resource_lease_job` (`job_id`),
  KEY `idx_resource_lease_project` (`project_id`,`lease_state`),
  KEY `idx_resource_lease_expires` (`lease_state`,`expires_at`),
  CONSTRAINT `fk_resource_lease_job` FOREIGN KEY (`job_id`) REFERENCES `execution_job` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源租约：resource_key 唯一即天然互斥';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `resource_lease` WRITE;
/*!40000 ALTER TABLE `resource_lease` DISABLE KEYS */;
/*!40000 ALTER TABLE `resource_lease` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `resource_lease_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `resource_lease_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_key` varchar(128) NOT NULL,
  `job_id` bigint NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `project_id` bigint NOT NULL,
  `acquired_at` datetime(3) NOT NULL,
  `released_at` datetime(3) DEFAULT NULL,
  `lease_state` varchar(16) NOT NULL COMMENT 'released/expired/revoked',
  `release_reason` varchar(64) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_lease_history_key_acquired` (`resource_key`,`acquired_at`),
  KEY `idx_lease_history_run` (`run_id`),
  KEY `idx_lease_history_project` (`project_id`,`lease_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租约历史（冲突判定证据）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `resource_lease_history` WRITE;
/*!40000 ALTER TABLE `resource_lease_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `resource_lease_history` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `retention_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `retention_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL,
  `artifact_kind` varchar(32) NOT NULL COMMENT 'output_xml/log_zip/pdf',
  `retention_days` int NOT NULL DEFAULT '180',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_retention_policy` (`project_id`,`artifact_kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产物保留策略（v0.5 启用）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `retention_policy` WRITE;
/*!40000 ALTER TABLE `retention_policy` DISABLE KEYS */;
/*!40000 ALTER TABLE `retention_policy` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `robot_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `robot_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `path` varchar(512) NOT NULL COMMENT '工程内相对路径',
  `content` longtext COMMENT '文件内容唯一真源（P5 冻结）',
  `content_sha256` char(64) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `current_version` int NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_robot_file_project_path` (`project_id`,`path`),
  CONSTRAINT `fk_robot_file_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RF 文件（内容即真源，不存 AST）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `robot_file` WRITE;
/*!40000 ALTER TABLE `robot_file` DISABLE KEYS */;
/*!40000 ALTER TABLE `robot_file` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `robot_file_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `robot_file_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `robot_file_id` bigint NOT NULL,
  `version_no` int NOT NULL,
  `content` longtext NOT NULL,
  `content_sha256` char(64) NOT NULL,
  `comment` varchar(512) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `content_size` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_robot_file_version` (`robot_file_id`,`version_no`),
  CONSTRAINT `fk_robot_file_version_file` FOREIGN KEY (`robot_file_id`) REFERENCES `robot_file` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RF 文件版本快照';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `robot_file_version` WRITE;
/*!40000 ALTER TABLE `robot_file_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `robot_file_version` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `built_in` tinyint NOT NULL DEFAULT '0' COMMENT '1=内置角色，不可删',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `permissions` text COMMENT '权限点 JSON 数组，如 ["case:read","case:write"]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`),
  KEY `idx_role_built_in` (`built_in`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `role` WRITE;
/*!40000 ALTER TABLE `role` DISABLE KEYS */;
/*!40000 ALTER TABLE `role` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `run` (
  `run_id` varchar(64) NOT NULL COMMENT '三方关联唯一键 RUN_ID',
  `run_request_id` varchar(64) DEFAULT NULL COMMENT '幂等键',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `test_set_id` bigint DEFAULT NULL,
  `trigger_type` varchar(16) NOT NULL DEFAULT 'manual' COMMENT 'manual/schedule/ci',
  `plan_id` bigint DEFAULT NULL COMMENT '触发本次运行的执行计划（手工运行可为空）',
  `triggered_by_plan_at` datetime(3) DEFAULT NULL COMMENT '计划触发点（用于反查本次 run 对应哪一次 cron 时间）',
  `priority` int NOT NULL DEFAULT '0',
  `enqueued_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `concurrency` int NOT NULL DEFAULT '1',
  `device_scope` varchar(512) DEFAULT NULL COMMENT '设备范围：逗号分隔的设备/端口键（如 DUT-01/mlx5_0）；NULL=未声明（不参与互斥）',
  `run_status` varchar(32) NOT NULL DEFAULT 'queued' COMMENT 'queued/preparing/running/finished/failed/canceled',
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `total_tests` int DEFAULT NULL,
  `passed_tests` int DEFAULT NULL,
  `failed_tests` int DEFAULT NULL,
  `artifacts_dir` varchar(512) DEFAULT NULL,
  `workspace_dir` varchar(512) DEFAULT NULL,
  `shard_count` int DEFAULT NULL,
  `lease_wait_ms` bigint DEFAULT NULL,
  `failure_reason` varchar(64) DEFAULT NULL,
  `failure_class` varchar(16) DEFAULT NULL,
  `failure_message` text,
  `retry_of_run_id` varchar(64) DEFAULT NULL,
  `attempt_no` int NOT NULL DEFAULT '0',
  `operator_name` varchar(64) DEFAULT NULL,
  `dut` varchar(40) DEFAULT NULL COMMENT 'metadata.DUT：被测设备',
  `link` varchar(40) DEFAULT NULL COMMENT 'metadata.LINK：被测链路（两端都写）',
  `sw_build` varchar(40) DEFAULT NULL COMMENT 'metadata.SW_BUILD：被测环境软件版本',
  `baseline` varchar(40) DEFAULT NULL COMMENT 'metadata.BASELINE：对照基线量级',
  `operator` varchar(40) DEFAULT NULL COMMENT 'metadata.OPERATOR：执行人',
  `case_kind` varchar(20) DEFAULT NULL COMMENT 'metadata.CASE_KIND：用例类型',
  `metadata_json` text COMMENT '完整 metadata 键值（JSON），供报告接口原样回传',
  `metadata_missing` varchar(200) DEFAULT NULL COMMENT '缺失的十项键名，逗号分隔；NULL 表示不缺失',
  `exportable` tinyint(1) NOT NULL DEFAULT '0' COMMENT '1=可对外交付（十项齐全且无超长）',
  `pass_count` int NOT NULL DEFAULT '0' COMMENT '解析得到的 PASS 用例数',
  `fail_count` int NOT NULL DEFAULT '0' COMMENT '解析得到的 FAIL 用例数',
  `skip_count` int NOT NULL DEFAULT '0' COMMENT '解析得到的 SKIP 用例数',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `node_id` bigint DEFAULT NULL COMMENT '非空表示该轮跑在指定执行节点上（NODX-02）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_run_id` (`run_id`),
  UNIQUE KEY `uk_run_project_request` (`project_id`,`run_request_id`),
  KEY `idx_run_project_started` (`project_id`,`started_at`),
  KEY `fk_run_test_set` (`test_set_id`),
  KEY `idx_run_plan` (`project_id`,`plan_id`),
  KEY `idx_run_node` (`node_id`),
  CONSTRAINT `fk_run_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_run_test_set` FOREIGN KEY (`test_set_id`) REFERENCES `test_set` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一次运行（v0.4/0.5 补字段）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `run` WRITE;
/*!40000 ALTER TABLE `run` DISABLE KEYS */;
/*!40000 ALTER TABLE `run` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `run_artifact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `run_artifact` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` bigint NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `kind` varchar(32) NOT NULL COMMENT 'output_xml/log_html/report_html/pdf',
  `shard_no` int DEFAULT NULL,
  `path` varchar(512) NOT NULL,
  `sha256` char(64) DEFAULT NULL,
  `size_bytes` bigint DEFAULT NULL,
  `line_count` bigint DEFAULT NULL,
  `availability` varchar(16) NOT NULL DEFAULT 'ok' COMMENT 'ok/missing/expired',
  `note` varchar(255) DEFAULT NULL,
  `pinned` tinyint NOT NULL DEFAULT '0' COMMENT '1=手动保留，不参与自动清理',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_run_artifact_run_kind` (`run_id`,`kind`),
  KEY `idx_run_artifact_project` (`project_id`,`run_id`),
  KEY `idx_run_artifact_shard` (`run_id`,`shard_no`),
  CONSTRAINT `fk_run_artifact_run` FOREIGN KEY (`run_id`) REFERENCES `run` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行产物登记';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `run_artifact` WRITE;
/*!40000 ALTER TABLE `run_artifact` DISABLE KEYS */;
/*!40000 ALTER TABLE `run_artifact` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `run_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `run_attempt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `attempt_no` int NOT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `verdict` varchar(16) DEFAULT NULL COMMENT 'PASS/FAIL/SKIP/ERROR',
  `failure_class` varchar(16) DEFAULT NULL,
  `failure_msg` text,
  `retried` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_attempt` (`project_id`,`run_id`,`attempt_no`),
  KEY `idx_run_attempt_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行尝试记录（重试可追溯）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `run_attempt` WRITE;
/*!40000 ALTER TABLE `run_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `run_attempt` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `run_metadata`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `run_metadata` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `run_id` varchar(64) NOT NULL COMMENT 'run.run_id，三方关联唯一键',
  `meta_key` varchar(64) NOT NULL COMMENT '元数据键，服务层限 [A-Z][A-Z0-9_]{0,39}',
  `meta_value` varchar(256) NOT NULL COMMENT '元数据值，服务层限 200 字符，不允许存凭据',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_metadata_run_key` (`run_id`,`meta_key`),
  KEY `idx_run_metadata_project` (`project_id`,`run_id`),
  CONSTRAINT `fk_run_metadata_run` FOREIGN KEY (`run_id`) REFERENCES `run` (`run_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行记录补录 metadata（键值对，逐项可审计）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `run_metadata` WRITE;
/*!40000 ALTER TABLE `run_metadata` DISABLE KEYS */;
/*!40000 ALTER TABLE `run_metadata` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `cron_expr` varchar(64) NOT NULL,
  `test_set_id` bigint DEFAULT NULL,
  `schedule_state` varchar(16) NOT NULL DEFAULT 'enabled' COMMENT 'enabled/paused',
  `next_fire_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_project_name` (`project_id`,`name`),
  KEY `idx_schedule_next` (`schedule_state`,`next_fire_at`),
  CONSTRAINT `fk_schedule_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时计划（v0.6 补字段）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `schedule` WRITE;
/*!40000 ALTER TABLE `schedule` DISABLE KEYS */;
/*!40000 ALTER TABLE `schedule` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `schedule_dependency`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule_dependency` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `schedule_id` bigint NOT NULL,
  `depends_on_schedule_id` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_dependency` (`schedule_id`,`depends_on_schedule_id`),
  KEY `fk_schedule_dependency_parent` (`depends_on_schedule_id`),
  CONSTRAINT `fk_schedule_dependency_parent` FOREIGN KEY (`depends_on_schedule_id`) REFERENCES `schedule` (`id`),
  CONSTRAINT `fk_schedule_dependency_self` FOREIGN KEY (`schedule_id`) REFERENCES `schedule` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划依赖';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `schedule_dependency` WRITE;
/*!40000 ALTER TABLE `schedule_dependency` DISABLE KEYS */;
/*!40000 ALTER TABLE `schedule_dependency` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `schedule_trigger_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule_trigger_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `schedule_id` bigint NOT NULL,
  `fired_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `trigger_result` varchar(16) NOT NULL COMMENT 'ok/fail/skipped',
  `run_key` varchar(64) DEFAULT NULL COMMENT '弱引用 RUN_ID，不加 FK',
  `message` varchar(512) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_schedule_trigger_log_schedule` (`schedule_id`,`fired_at`),
  CONSTRAINT `fk_schedule_trigger_log_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `schedule` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划触发日志';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `schedule_trigger_log` WRITE;
/*!40000 ALTER TABLE `schedule_trigger_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `schedule_trigger_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `system_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `setting_key` varchar(128) NOT NULL,
  `setting_value` text,
  `description` varchar(512) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统设置';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `system_setting` WRITE;
/*!40000 ALTER TABLE `system_setting` DISABLE KEYS */;
/*!40000 ALTER TABLE `system_setting` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `test_case`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_case` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `case_code` varchar(64) NOT NULL COMMENT '台账编号，如 PERF-IPERF-200G-001',
  `title` varchar(256) NOT NULL,
  `case_kind` varchar(32) DEFAULT NULL COMMENT 'func/perf/rdma/...',
  `source_type` varchar(16) NOT NULL DEFAULT 'ledger' COMMENT 'ledger/script',
  `priority` varchar(8) DEFAULT NULL,
  `safety_level` varchar(8) NOT NULL DEFAULT 'LX' COMMENT '执行安全级：L0 只读 / L1 可逆写 / L2 高危 / LX 未判定（ADR-0013）',
  `domain_tag` varchar(64) DEFAULT NULL COMMENT '技术域标签',
  `status` tinyint NOT NULL DEFAULT '1',
  `description` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `precondition` text,
  `expected` text,
  `review_status` varchar(16) NOT NULL DEFAULT 'draft' COMMENT 'draft/pending/approved/rejected',
  `review_comment` varchar(512) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime(3) DEFAULT NULL,
  `source` varchar(32) NOT NULL DEFAULT 'import' COMMENT 'import/manual/api',
  `capability` varchar(64) DEFAULT NULL COMMENT '能力维度，如 roce-connectivity / pfc-backpressure',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_test_case_project_code` (`project_id`,`case_code`),
  KEY `idx_test_case_domain` (`project_id`,`domain_tag`),
  KEY `idx_test_case_review` (`project_id`,`review_status`),
  KEY `idx_test_case_priority` (`project_id`,`priority`),
  KEY `idx_test_case_capability` (`project_id`,`capability`),
  KEY `idx_test_case_safety` (`project_id`,`safety_level`),
  CONSTRAINT `fk_test_case_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用例台账';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `test_case` WRITE;
/*!40000 ALTER TABLE `test_case` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_case` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `test_case_step`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_case_step` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `test_case_id` bigint NOT NULL,
  `step_no` int NOT NULL,
  `action` text NOT NULL,
  `expectation` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `source_row` int DEFAULT NULL COMMENT 'Excel 原始行号，导入对账用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_test_case_step` (`test_case_id`,`step_no`),
  CONSTRAINT `fk_test_case_step_case` FOREIGN KEY (`test_case_id`) REFERENCES `test_case` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用例步骤台账';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `test_case_step` WRITE;
/*!40000 ALTER TABLE `test_case_step` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_case_step` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `test_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `title` varchar(200) NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `version` int NOT NULL DEFAULT '1',
  `snapshot_hash` char(64) DEFAULT NULL,
  `snapshot_json` longtext,
  `snapshot_html` longtext,
  `source_summary` text,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `frozen_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_run_version` (`project_id`,`run_id`,`version`),
  KEY `idx_report_project_state` (`project_id`,`state`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `test_report` WRITE;
/*!40000 ALTER TABLE `test_report` DISABLE KEYS */;
INSERT INTO `test_report` VALUES (1,9001,'TC32-RUN-1','TC32 报表 1','DRAFT',1,NULL,NULL,NULL,NULL,NULL,'2026-11-07 11:03:46.707','2026-11-07 11:03:46.707',NULL,NULL),(2,9001,'TC32-RUN-2','TC32 报表 2','FROZEN',1,NULL,NULL,NULL,NULL,NULL,'2026-11-07 11:03:46.707','2026-11-07 11:03:46.707',NULL,NULL);
/*!40000 ALTER TABLE `test_report` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `test_report_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_report_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint NOT NULL,
  `action` varchar(32) NOT NULL,
  `from_state` varchar(16) DEFAULT NULL,
  `to_state` varchar(16) DEFAULT NULL,
  `actor_id` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_report_audit` (`report_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `test_report_audit` WRITE;
/*!40000 ALTER TABLE `test_report_audit` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_report_audit` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `test_report_export`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_report_export` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint NOT NULL,
  `format` varchar(8) NOT NULL,
  `file_name` varchar(200) NOT NULL,
  `size_bytes` bigint NOT NULL DEFAULT '0',
  `watermark` varchar(64) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_export_report` (`report_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `test_report_export` WRITE;
/*!40000 ALTER TABLE `test_report_export` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_report_export` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `test_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` bigint NOT NULL,
  `case_id` bigint DEFAULT NULL,
  `case_key` varchar(160) NOT NULL DEFAULT '' COMMENT 'suitePath.caseName：跨 run 对比的连接键',
  `round_no` int NOT NULL DEFAULT '1' COMMENT '每例三遍的第几轮',
  `verdict` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PASS/FAIL/SKIP',
  `duration_ms` bigint DEFAULT NULL,
  `log_offset` bigint DEFAULT NULL COMMENT '日志文件行偏移（RPT-02 回填）',
  `log_span` varchar(64) DEFAULT NULL COMMENT '用例日志序号区间 start:end（左闭右开）',
  `failure_msg` text,
  `failure_kind` varchar(16) DEFAULT NULL COMMENT 'assert/env/infra：与 v0.4 失败分类对齐',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_test_result_run_case` (`run_id`,`case_id`),
  KEY `idx_test_result_case` (`case_id`),
  CONSTRAINT `fk_test_result_run` FOREIGN KEY (`run_id`) REFERENCES `run` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用例×轮次×结论（v0.5 补字段）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `test_result` WRITE;
/*!40000 ALTER TABLE `test_result` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_result` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `test_scenario`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_scenario` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `name` varchar(150) NOT NULL,
  `case_set_ids` text,
  `parent_scenario_id` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_scenario_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `test_scenario` WRITE;
/*!40000 ALTER TABLE `test_scenario` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_scenario` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `test_set`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_set` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL COMMENT 'PLATFORM 资产为 NULL',
  `set_code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `owner_id` bigint DEFAULT NULL,
  `set_status` varchar(16) NOT NULL DEFAULT 'active' COMMENT 'active/archived',
  `owner_scope` varchar(16) NOT NULL DEFAULT 'PROJECT' COMMENT 'PROJECT 项目私有 / PLATFORM 平台共享',
  `source_asset_id` bigint DEFAULT NULL COMMENT '非空表示本项目这份是平台资产的副本，指向平台 test_set.id',
  `scope_project_id` bigint GENERATED ALWAYS AS (if((`owner_scope` = _utf8mb4'PLATFORM'),0,`project_id`)) VIRTUAL COMMENT '唯一性辅助列（平台=0，私有=project_id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_test_set_scope_code` (`scope_project_id`,`set_code`),
  KEY `idx_test_set_project` (`project_id`),
  KEY `idx_test_set_source` (`source_asset_id`),
  CONSTRAINT `fk_test_set_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用例集';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `test_set` WRITE;
/*!40000 ALTER TABLE `test_set` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_set` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `test_set_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_set_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `test_set_id` bigint NOT NULL,
  `test_case_id` bigint NOT NULL,
  `seq_no` int NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `item_source` varchar(32) DEFAULT NULL COMMENT 'manual/import',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_test_set_item` (`test_set_id`,`seq_no`),
  KEY `idx_test_set_item_case` (`test_case_id`),
  CONSTRAINT `fk_test_set_item_case` FOREIGN KEY (`test_case_id`) REFERENCES `test_case` (`id`),
  CONSTRAINT `fk_test_set_item_set` FOREIGN KEY (`test_set_id`) REFERENCES `test_set` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用例集条目（按序执行）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `test_set_item` WRITE;
/*!40000 ALTER TABLE `test_set_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_set_item` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `topology_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `topology_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `node_type` varchar(32) NOT NULL COMMENT 'rack/host/switch/storage',
  `ref_device_id` bigint DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `description` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topology_node_project_name` (`project_id`,`name`),
  KEY `idx_topology_node_parent` (`parent_id`),
  CONSTRAINT `fk_topology_node_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拓扑节点';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `topology_node` WRITE;
/*!40000 ALTER TABLE `topology_node` DISABLE KEYS */;
/*!40000 ALTER TABLE `topology_node` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `user_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_group` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `description` varchar(400) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user_group` WRITE;
/*!40000 ALTER TABLE `user_group` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_group` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `user_group_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_group_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_member` (`group_id`,`user_id`),
  KEY `idx_member_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user_group_member` WRITE;
/*!40000 ALTER TABLE `user_group_member` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_group_member` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `user_preference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `pref_key` varchar(64) NOT NULL COMMENT '偏好键，只接受白名单内的值',
  `pref_value` varchar(255) NOT NULL COMMENT '偏好值，长度上限 255，不允许存凭据',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL COMMENT '创建人；偏好表里恒等于 user_id，保留是为了与全库审计规范一致',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_preference` (`user_id`,`pref_key`),
  CONSTRAINT `fk_user_preference_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户偏好（跨设备一致）；删用户自动清理';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user_preference` WRITE;
/*!40000 ALTER TABLE `user_preference` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_preference` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `fk_user_role_role` (`role_id`),
  CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`),
  CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user_role` WRITE;
/*!40000 ALTER TABLE `user_role` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_role` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


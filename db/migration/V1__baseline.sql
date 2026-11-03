-- =============================================================================
-- V1__baseline.sql  ·  Robot-Autotest schema v1（38 张表，7 域）
-- 归属 WS-04 ｜ 门禁 G1 ｜ 对应 FR-v0.1-12 ~ 15
--
-- 本脚本只建「结构必然存在」的部分：主键、业务唯一键、强关系外键、必要索引。
-- 字段随版本用新增迁移补齐（规划 §5.2 常见坑②），禁止修改本文件的历史字节。
--
-- 统一约定（db/README.md 有完整说明）：
--   1. 主键 id BIGINT AUTO_INCREMENT；业务唯一键统一 uk_ 前缀。
--   2. 每表必须有审计四列：created_at / created_by / updated_at / updated_by。
--   3. 外键只加在强关系上；弱引用（如 audit_log.resource_id）只建索引不加 FK。
--   4. 时间统一 DATETIME(3)，字符集统一 utf8mb4，引擎统一 InnoDB。
--   5. 表名 snake_case 单数，按域前缀命名（app_/project_/device_/test_/execution_/...）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 域 1/7 · 项目（3）—— 多项目隔离的根，先建以便其他域引用
-- -----------------------------------------------------------------------------

CREATE TABLE `project` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  code          VARCHAR(64)  NOT NULL COMMENT '项目代号，对外引用用这个',
  name          VARCHAR(128) NOT NULL,
  description   VARCHAR(512) NULL,
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_code (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目';

CREATE TABLE `project_setting` (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  project_id    BIGINT        NOT NULL,
  setting_key   VARCHAR(128)  NOT NULL,
  setting_value TEXT          NULL,
  created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT        NULL,
  updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT        NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_setting_key (project_id, setting_key),
  CONSTRAINT fk_project_setting_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目级配置项';

CREATE TABLE `project_label` (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  project_id    BIGINT        NOT NULL,
  label_key     VARCHAR(64)   NOT NULL,
  label_value   VARCHAR(128)  NOT NULL,
  created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT        NULL,
  updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT        NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_label (project_id, label_key, label_value),
  CONSTRAINT fk_project_label_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目标签（技术域等）';

-- -----------------------------------------------------------------------------
-- 域 2/7 · 身份（5）
-- -----------------------------------------------------------------------------

CREATE TABLE `app_user` (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)   NOT NULL,
  display_name  VARCHAR(128)  NULL,
  email         VARCHAR(190)  NULL,
  password_hash VARCHAR(100)  NOT NULL COMMENT '只存哈希，禁止明文',
  status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
  last_login_at DATETIME(3)   NULL,
  created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT        NULL,
  updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT        NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_user_username (username),
  UNIQUE KEY uk_app_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户';

CREATE TABLE `role` (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  code          VARCHAR(64)   NOT NULL,
  name          VARCHAR(128)  NOT NULL,
  description   VARCHAR(512)  NULL,
  built_in      TINYINT       NOT NULL DEFAULT 0 COMMENT '1=内置角色，不可删',
  created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT        NULL,
  updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT        NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE `user_role` (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  user_id       BIGINT      NOT NULL,
  role_id       BIGINT      NOT NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT      NULL,
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES `app_user` (id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES `role` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色';

CREATE TABLE `project_member` (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  project_id    BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  member_role   VARCHAR(32) NOT NULL DEFAULT 'member' COMMENT 'owner/member/viewer',
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT      NULL,
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_member (project_id, user_id),
  CONSTRAINT fk_project_member_project FOREIGN KEY (project_id) REFERENCES `project` (id),
  CONSTRAINT fk_project_member_user FOREIGN KEY (user_id) REFERENCES `app_user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成员（行级隔离的依据）';

CREATE TABLE `api_token` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NOT NULL,
  name          VARCHAR(128) NOT NULL,
  token_hash    VARCHAR(100) NOT NULL COMMENT '只存哈希',
  last_used_at  DATETIME(3)  NULL,
  expires_at    DATETIME(3)  NULL,
  revoked_at    DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_api_token_hash (token_hash),
  KEY idx_api_token_user (user_id),
  CONSTRAINT fk_api_token_user FOREIGN KEY (user_id) REFERENCES `app_user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 令牌';

-- -----------------------------------------------------------------------------
-- 域 3/7 · 资产（7）
-- -----------------------------------------------------------------------------

CREATE TABLE `device` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  name          VARCHAR(128) NOT NULL,
  device_type   VARCHAR(64)  NULL COMMENT 'gpu/server/switch/storage',
  management_ip VARCHAR(64)  NULL,
  status        TINYINT      NOT NULL DEFAULT 1,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_project_name (project_id, name),
  CONSTRAINT fk_device_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备';

CREATE TABLE `device_port` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  device_id     BIGINT       NOT NULL,
  port_name     VARCHAR(64)  NOT NULL,
  port_type     VARCHAR(32)  NULL COMMENT 'eth/ib/mgmt',
  mac_address   VARCHAR(32)  NULL,
  speed_gbps    INT          NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_port_name (device_id, port_name),
  CONSTRAINT fk_device_port_device FOREIGN KEY (device_id) REFERENCES `device` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备端口';

CREATE TABLE `link` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  name          VARCHAR(128) NOT NULL,
  link_type     VARCHAR(32)  NULL COMMENT 'rdma/eth/ib',
  a_device_id   BIGINT       NULL,
  a_port_id     BIGINT       NULL,
  b_device_id   BIGINT       NULL,
  b_port_id     BIGINT       NULL,
  status        TINYINT      NOT NULL DEFAULT 1,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_link_project_name (project_id, name),
  KEY idx_link_a_port (a_port_id),
  KEY idx_link_b_port (b_port_id),
  CONSTRAINT fk_link_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链路（两端都记）';

CREATE TABLE `credential` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  name          VARCHAR(128) NOT NULL,
  cred_type     VARCHAR(32)  NOT NULL COMMENT 'ssh/token/snmp',
  username      VARCHAR(128) NULL,
  secret_ref    VARCHAR(256) NOT NULL COMMENT '指向环境变量或密钥库，禁止存明文口令',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_credential_project_name (project_id, name),
  CONSTRAINT fk_credential_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='凭据引用（不存明文）';

CREATE TABLE `device_check` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  device_id     BIGINT       NOT NULL,
  check_type    VARCHAR(32)  NOT NULL COMMENT 'ping/ssh/port',
  check_result  VARCHAR(16)  NOT NULL COMMENT 'ok/fail',
  detail        TEXT         NULL,
  checked_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_device_check_device (device_id, check_type, checked_at),
  CONSTRAINT fk_device_check_device FOREIGN KEY (device_id) REFERENCES `device` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备联通检查记录';

CREATE TABLE `env_profile` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  name          VARCHAR(128) NOT NULL,
  profile_type  VARCHAR(64)  NULL COMMENT 'os/ofed/driver 基线画像',
  description   VARCHAR(512) NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_env_profile_project_name (project_id, name),
  CONSTRAINT fk_env_profile_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境画像';

CREATE TABLE `topology_node` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  name          VARCHAR(128) NOT NULL,
  node_type     VARCHAR(32)  NOT NULL COMMENT 'rack/host/switch/storage',
  ref_device_id BIGINT       NULL,
  parent_id     BIGINT       NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_topology_node_project_name (project_id, name),
  KEY idx_topology_node_parent (parent_id),
  CONSTRAINT fk_topology_node_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拓扑节点';

-- -----------------------------------------------------------------------------
-- 域 4/7 · 用例（7）—— 双模模型：test_case 台账 + robot_file.content 唯一真源
-- -----------------------------------------------------------------------------

CREATE TABLE `test_case` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  case_code     VARCHAR(64)  NOT NULL COMMENT '台账编号，如 PERF-IPERF-200G-001',
  title         VARCHAR(256) NOT NULL,
  case_kind     VARCHAR(32)  NULL COMMENT 'func/perf/rdma/...',
  source_type   VARCHAR(16)  NOT NULL DEFAULT 'ledger' COMMENT 'ledger/script',
  priority      VARCHAR(8)   NULL,
  domain_tag    VARCHAR(64)  NULL COMMENT '技术域标签',
  status        TINYINT      NOT NULL DEFAULT 1,
  description   TEXT         NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_test_case_project_code (project_id, case_code),
  KEY idx_test_case_domain (project_id, domain_tag),
  CONSTRAINT fk_test_case_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用例台账';

CREATE TABLE `test_case_step` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  test_case_id  BIGINT       NOT NULL,
  step_no       INT          NOT NULL,
  action        TEXT         NOT NULL,
  expectation   TEXT         NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_test_case_step (test_case_id, step_no),
  CONSTRAINT fk_test_case_step_case FOREIGN KEY (test_case_id) REFERENCES `test_case` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用例步骤台账';

CREATE TABLE `test_set` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  set_code      VARCHAR(64)  NOT NULL,
  name          VARCHAR(128) NOT NULL,
  description   VARCHAR(512) NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_test_set_project_code (project_id, set_code),
  CONSTRAINT fk_test_set_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用例集';

CREATE TABLE `test_set_item` (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  test_set_id   BIGINT      NOT NULL,
  test_case_id  BIGINT      NOT NULL,
  seq_no        INT         NOT NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT      NULL,
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_test_set_item (test_set_id, seq_no),
  KEY idx_test_set_item_case (test_case_id),
  CONSTRAINT fk_test_set_item_set FOREIGN KEY (test_set_id) REFERENCES `test_set` (id),
  CONSTRAINT fk_test_set_item_case FOREIGN KEY (test_case_id) REFERENCES `test_case` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用例集条目（按序执行）';

CREATE TABLE `robot_file` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  path          VARCHAR(512) NOT NULL COMMENT '工程内相对路径',
  content       LONGTEXT     NULL COMMENT '文件内容唯一真源（P5 冻结）',
  content_sha256 CHAR(64)    NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_robot_file_project_path (project_id, path),
  CONSTRAINT fk_robot_file_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RF 文件（内容即真源，不存 AST）';

CREATE TABLE `robot_file_version` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  robot_file_id BIGINT       NOT NULL,
  version_no    INT          NOT NULL,
  content       LONGTEXT     NOT NULL,
  content_sha256 CHAR(64)    NOT NULL,
  comment       VARCHAR(512) NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_robot_file_version (robot_file_id, version_no),
  CONSTRAINT fk_robot_file_version_file FOREIGN KEY (robot_file_id) REFERENCES `robot_file` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RF 文件版本快照';

CREATE TABLE `case_requirement` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  test_case_id  BIGINT       NOT NULL,
  req_key       VARCHAR(64)  NOT NULL,
  req_value     VARCHAR(512) NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_case_requirement (test_case_id, req_key),
  CONSTRAINT fk_case_requirement_case FOREIGN KEY (test_case_id) REFERENCES `test_case` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用例前置条件 / 追溯';

-- -----------------------------------------------------------------------------
-- 域 5/7 · 执行（8）
-- -----------------------------------------------------------------------------

CREATE TABLE `execution_job` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  job_no        VARCHAR(64)  NOT NULL,
  job_status    VARCHAR(32)  NOT NULL DEFAULT 'submitted' COMMENT 'submitted/queued/running/finished/cancelled',
  submitted_by  BIGINT       NULL,
  submitted_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_execution_job_no (job_no),
  KEY idx_execution_job_project (project_id, job_status),
  CONSTRAINT fk_execution_job_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行提交（v0.4 补字段）';

CREATE TABLE `execution_queue` (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  job_id        BIGINT      NOT NULL,
  priority      INT         NOT NULL DEFAULT 0,
  queue_state   VARCHAR(16) NOT NULL DEFAULT 'waiting' COMMENT 'waiting/running/done',
  enqueued_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  started_at    DATETIME(3) NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT      NULL,
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_execution_queue_job (job_id),
  KEY idx_execution_queue_state (queue_state, priority),
  CONSTRAINT fk_execution_queue_job FOREIGN KEY (job_id) REFERENCES `execution_job` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行队列（v0.4 补字段）';

CREATE TABLE `resource_lease` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  job_id        BIGINT       NOT NULL,
  resource_key  VARCHAR(128) NOT NULL COMMENT '设备#端口粒度，业务主键；唯一即互斥',
  lease_state   VARCHAR(16)  NOT NULL DEFAULT 'held' COMMENT 'held/released/expired',
  leased_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  expires_at    DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_resource_lease_key (resource_key),
  KEY idx_resource_lease_job (job_id),
  CONSTRAINT fk_resource_lease_job FOREIGN KEY (job_id) REFERENCES `execution_job` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源租约：resource_key 唯一即天然互斥';

CREATE TABLE `execution_event` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  job_id        BIGINT       NULL,
  run_key       VARCHAR(64)  NULL COMMENT '弱引用 run.run_id，不加 FK',
  event_type    VARCHAR(64)  NOT NULL,
  payload       TEXT         NULL,
  occurred_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_execution_event_job (job_id, occurred_at),
  KEY idx_execution_event_run (run_key, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行事件流';

CREATE TABLE `run` (
  run_id        VARCHAR(64)  NOT NULL COMMENT '三方关联唯一键 RUN_ID',
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  test_set_id   BIGINT       NULL,
  trigger_type  VARCHAR(32)  NOT NULL DEFAULT 'manual' COMMENT 'manual/cron/ci',
  run_status    VARCHAR(32)  NOT NULL DEFAULT 'created' COMMENT 'created/running/passed/failed/cancelled',
  started_at    DATETIME(3)  NULL,
  finished_at   DATETIME(3)  NULL,
  operator_name VARCHAR(64)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_run_run_id (run_id),
  KEY idx_run_project_started (project_id, started_at),
  CONSTRAINT fk_run_project FOREIGN KEY (project_id) REFERENCES `project` (id),
  CONSTRAINT fk_run_test_set FOREIGN KEY (test_set_id) REFERENCES `test_set` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次运行（v0.4/0.5 补字段）';

CREATE TABLE `test_result` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  run_id        BIGINT       NOT NULL,
  case_id       BIGINT       NULL,
  round_no      INT          NOT NULL DEFAULT 1 COMMENT '每例三遍的第几轮',
  verdict       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PASS/FAIL/SKIP',
  duration_ms   BIGINT       NULL,
  failure_msg   TEXT         NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_test_result_run_case (run_id, case_id),
  KEY idx_test_result_case (case_id),
  CONSTRAINT fk_test_result_run FOREIGN KEY (run_id) REFERENCES `run` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用例×轮次×结论（v0.5 补字段）';

CREATE TABLE `keyword_result` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  run_id        BIGINT       NOT NULL,
  test_result_id BIGINT      NULL,
  parent_id     BIGINT       NULL,
  kw_name       VARCHAR(256) NOT NULL,
  kw_status     VARCHAR(16)  NULL,
  depth         INT          NOT NULL DEFAULT 0,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_keyword_result_run (run_id),
  KEY idx_keyword_result_test_result (test_result_id),
  CONSTRAINT fk_keyword_result_run FOREIGN KEY (run_id) REFERENCES `run` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关键字结果树（v0.5 补字段）';

CREATE TABLE `run_artifact` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  run_id        BIGINT       NOT NULL,
  kind          VARCHAR(32)  NOT NULL COMMENT 'output_xml/log_html/report_html/pdf',
  path          VARCHAR(512) NOT NULL,
  sha256        CHAR(64)     NULL,
  size_bytes    BIGINT       NULL,
  availability  VARCHAR(16)  NOT NULL DEFAULT 'ok' COMMENT 'ok/missing/expired',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_run_artifact_run_kind (run_id, kind),
  CONSTRAINT fk_run_artifact_run FOREIGN KEY (run_id) REFERENCES `run` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行产物登记';

-- -----------------------------------------------------------------------------
-- 域 6/7 · 计划（3）
-- -----------------------------------------------------------------------------

CREATE TABLE `schedule` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  name          VARCHAR(128) NOT NULL,
  cron_expr     VARCHAR(64)  NOT NULL,
  test_set_id   BIGINT       NULL,
  schedule_state VARCHAR(16) NOT NULL DEFAULT 'enabled' COMMENT 'enabled/paused',
  next_fire_at  DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_schedule_project_name (project_id, name),
  KEY idx_schedule_next (schedule_state, next_fire_at),
  CONSTRAINT fk_schedule_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时计划（v0.6 补字段）';

CREATE TABLE `schedule_dependency` (
  id                    BIGINT      NOT NULL AUTO_INCREMENT,
  schedule_id           BIGINT      NOT NULL,
  depends_on_schedule_id BIGINT     NOT NULL,
  created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by            BIGINT      NULL,
  updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by            BIGINT      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_schedule_dependency (schedule_id, depends_on_schedule_id),
  CONSTRAINT fk_schedule_dependency_self FOREIGN KEY (schedule_id) REFERENCES `schedule` (id),
  CONSTRAINT fk_schedule_dependency_parent FOREIGN KEY (depends_on_schedule_id) REFERENCES `schedule` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划依赖';

CREATE TABLE `schedule_trigger_log` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  schedule_id   BIGINT       NOT NULL,
  fired_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  trigger_result VARCHAR(16) NOT NULL COMMENT 'ok/fail/skipped',
  run_key       VARCHAR(64)  NULL COMMENT '弱引用 RUN_ID，不加 FK',
  message       VARCHAR(512) NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_schedule_trigger_log_schedule (schedule_id, fired_at),
  CONSTRAINT fk_schedule_trigger_log_schedule FOREIGN KEY (schedule_id) REFERENCES `schedule` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划触发日志';

-- -----------------------------------------------------------------------------
-- 域 7/7 · 治理（5）
-- -----------------------------------------------------------------------------

CREATE TABLE `audit_log` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NULL COMMENT '弱引用，不加 FK',
  actor_id      BIGINT       NULL COMMENT '弱引用 app_user，不加 FK',
  action        VARCHAR(128) NOT NULL,
  resource_type VARCHAR(64)  NULL,
  resource_id   VARCHAR(64)  NULL COMMENT '弱引用，不加 FK',
  detail        TEXT         NULL,
  occurred_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_audit_log_project_time (project_id, occurred_at),
  KEY idx_audit_log_actor_time (actor_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志（v1.0 启用）';

CREATE TABLE `notification` (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  project_id        BIGINT       NULL,
  run_key           VARCHAR(64)  NULL COMMENT '弱引用 RUN_ID',
  channel           VARCHAR(32)  NOT NULL DEFAULT 'web',
  notification_type VARCHAR(32)  NOT NULL,
  notify_status     VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/sent/failed',
  message           VARCHAR(512) NULL,
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by        BIGINT       NULL,
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by        BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_dedup (run_key, channel, notification_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知（按 run+channel+type 去重）';

CREATE TABLE `system_setting` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  setting_key   VARCHAR(128) NOT NULL,
  setting_value TEXT         NULL,
  description   VARCHAR(512) NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_setting_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统设置';

CREATE TABLE `retention_policy` (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  project_id     BIGINT      NULL,
  artifact_kind  VARCHAR(32) NOT NULL COMMENT 'output_xml/log_zip/pdf',
  retention_days INT         NOT NULL DEFAULT 180,
  enabled        TINYINT     NOT NULL DEFAULT 1,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by     BIGINT      NULL,
  updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by     BIGINT      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_retention_policy (project_id, artifact_kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产物保留策略（v0.5 启用）';

CREATE TABLE `export_record` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  run_key       VARCHAR(64)  NULL COMMENT '弱引用 RUN_ID',
  export_format VARCHAR(16)  NOT NULL COMMENT 'pdf/docx/xlsx',
  file_path     VARCHAR(512) NULL,
  export_status VARCHAR(16)  NOT NULL DEFAULT 'created' COMMENT 'created/failed/expired',
  requested_by  BIGINT       NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_export_record_run (run_key),
  CONSTRAINT fk_export_record_project FOREIGN KEY (project_id) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导出记录（v0.5 启用）';

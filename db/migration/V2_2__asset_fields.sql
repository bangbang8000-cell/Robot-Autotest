-- =============================================================================
-- V2_2__asset_fields.sql  ·  v0.2 资产台账业务字段（7 张表）
-- 归属 AST-01 ~ AST-04 ｜ 门禁 G2 ｜ 对应 FR-v0.2-09 ~ 15
-- =============================================================================

-- 1) 设备：型号、序列号（项目内唯一）、位置、技术域
ALTER TABLE `device`
  ADD COLUMN model        VARCHAR(128) NULL,
  ADD COLUMN vendor       VARCHAR(64)  NULL,
  ADD COLUMN serial_no    VARCHAR(128) NULL COMMENT '序列号，项目内唯一',
  ADD COLUMN location     VARCHAR(128) NULL,
  ADD COLUMN rack_no      VARCHAR(32)  NULL,
  ADD COLUMN domain_tag   VARCHAR(64)  NULL COMMENT '技术域标签：rdma/ethernet/qos/virt/offload/gpu',
  ADD COLUMN owner_id     BIGINT       NULL,
  ADD COLUMN last_checked_at DATETIME(3) NULL;

ALTER TABLE `device` ADD UNIQUE KEY uk_device_project_serial (project_id, serial_no);
ALTER TABLE `device` ADD INDEX idx_device_domain (project_id, domain_tag);

-- 2) 端口：协议、状态、MTU、IP
ALTER TABLE `device_port`
  ADD COLUMN protocol    VARCHAR(32) NOT NULL DEFAULT 'ethernet' COMMENT 'ethernet/rdma/ib/mgmt',
  ADD COLUMN port_status VARCHAR(16) NOT NULL DEFAULT 'up' COMMENT 'up/down/disabled',
  ADD COLUMN mtu         INT         NULL,
  ADD COLUMN ip_address  VARCHAR(64) NULL;

ALTER TABLE `device_port` ADD INDEX idx_device_port_protocol (device_id, protocol);

-- 3) 链路：速率、MTU、协议、说明（DUT — LINK — HOST 三元链的参数）
ALTER TABLE `link`
  ADD COLUMN speed_gbps INT          NULL,
  ADD COLUMN mtu        INT          NULL,
  ADD COLUMN protocol   VARCHAR(32)  NULL COMMENT 'ethernet/rdma/ib',
  ADD COLUMN description VARCHAR(512) NULL;

-- 4) 凭据：密文与密钥版本（明文永不落盘，FR-v0.2-13）
ALTER TABLE `credential`
  ADD COLUMN cipher_blob     TEXT      NULL COMMENT 'AES-256-GCM 密文（含 IV），唯一存储形态',
  ADD COLUMN key_version     INT       NOT NULL DEFAULT 1,
  ADD COLUMN last_rotated_at DATETIME(3) NULL;

-- 5) 环境画像：快照 JSON 与快照时间（AST-02 环境快照 diff）
ALTER TABLE `env_profile`
  ADD COLUMN snapshot_json LONGTEXT    NULL COMMENT '拓扑与关键参数快照 JSON',
  ADD COLUMN snapshot_at   DATETIME(3) NULL;

-- 6) 拓扑节点：说明
ALTER TABLE `topology_node`
  ADD COLUMN description VARCHAR(512) NULL;

-- 7) 设备检查：延迟
ALTER TABLE `device_check`
  ADD COLUMN latency_ms INT NULL;

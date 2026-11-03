-- V2_2__asset_fields.down.sql  ·  回退 v0.2 资产字段
ALTER TABLE `device_check` DROP COLUMN latency_ms;

ALTER TABLE `topology_node` DROP COLUMN description;

ALTER TABLE `env_profile` DROP COLUMN snapshot_at;
ALTER TABLE `env_profile` DROP COLUMN snapshot_json;

ALTER TABLE `credential` DROP COLUMN last_rotated_at;
ALTER TABLE `credential` DROP COLUMN key_version;
ALTER TABLE `credential` DROP COLUMN cipher_blob;

ALTER TABLE `link` DROP COLUMN description;
ALTER TABLE `link` DROP COLUMN protocol;
ALTER TABLE `link` DROP COLUMN mtu;
ALTER TABLE `link` DROP COLUMN speed_gbps;

ALTER TABLE `device_port` DROP INDEX idx_device_port_protocol;
ALTER TABLE `device_port` DROP COLUMN ip_address;
ALTER TABLE `device_port` DROP COLUMN mtu;
ALTER TABLE `device_port` DROP COLUMN port_status;
ALTER TABLE `device_port` DROP COLUMN protocol;

ALTER TABLE `device` DROP INDEX idx_device_domain;
ALTER TABLE `device` DROP INDEX uk_device_project_serial;
ALTER TABLE `device` DROP COLUMN last_checked_at;
ALTER TABLE `device` DROP COLUMN owner_id;
ALTER TABLE `device` DROP COLUMN domain_tag;
ALTER TABLE `device` DROP COLUMN rack_no;
ALTER TABLE `device` DROP COLUMN location;
ALTER TABLE `device` DROP COLUMN serial_no;
ALTER TABLE `device` DROP COLUMN vendor;
ALTER TABLE `device` DROP COLUMN model;

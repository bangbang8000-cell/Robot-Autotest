-- V7_4 down：撤销运行设备范围

ALTER TABLE `run`
  DROP COLUMN `device_scope`;

-- V7_4 执行域：运行设备范围（v0.4 EXEC-11 / FR-v0.4-01）
--
-- 版本号取 V7_4 而非 V4_4：V4_1~V4_3、V5/V6/V7 系列均已在开发库执行过，
-- 补插低位版本会触发 Flyway out-of-order 校验失败（版本号只保证递增，不保证语义分组）。
-- 语义归属仍是 v0.4 执行包，见 docs/evidence/v0.4/exec11/README.md。

ALTER TABLE `run`
  ADD COLUMN `device_scope` VARCHAR(512) NULL
    COMMENT '设备范围：逗号分隔的设备/端口键（如 DUT-01/mlx5_0）；NULL=未声明（不参与互斥）'
    AFTER `concurrency`;

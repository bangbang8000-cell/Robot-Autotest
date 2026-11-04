-- V7_1__audit_summary.down.sql  ·  回退 v1.0 审计摘要列
-- 注意：目标库为 MySQL 8 / MariaDB，均不保证支持 `DROP COLUMN IF EXISTS`
-- （2026-11-04 G1 #4 现场复跑报 ERROR 1064）。标准回滚序列里这三列必由
-- V7_1 up 脚本创建，故直接 DROP；db-undo 本身不是幂等操作（见 V1 down）。
ALTER TABLE `audit_log`
  DROP COLUMN `ip`,
  DROP COLUMN `summary`,
  DROP COLUMN `operator`;

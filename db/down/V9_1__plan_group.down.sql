-- V9_1 回退：先删成员（有外键），再删组。
-- 注意：本回退**不动** execution_plan_dependency —— 已生成的边属于计划自身配置，
-- 回退组功能不应改变计划的调度行为（见正向迁移口径第 4 条）。

DROP TABLE IF EXISTS `plan_group_item`;
DROP TABLE IF EXISTS `plan_group`;

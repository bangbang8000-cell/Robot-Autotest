-- V6_2 计划依赖 DAG（PLN-02）
-- 口径见 docs/release/v0.6-automation/DEV-PLAN.md §3。
--   1. plan_id 是「后置计划」，depends_on_plan_id 是「前置计划」；有向边 前置 -> 后置。
--   2. UNIQUE(plan_id, depends_on_plan_id) 保证同一条依赖不重复保存。
--   3. 环检测在应用层（PlanDependencyGraph）保存前完成，库层只做形状约束，不写触发器。

CREATE TABLE `execution_plan_dependency` (
  id                 BIGINT      NOT NULL AUTO_INCREMENT,
  project_id         BIGINT      NOT NULL,
  plan_id            BIGINT      NOT NULL COMMENT '后置计划',
  depends_on_plan_id BIGINT      NOT NULL COMMENT '前置计划',
  created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by         BIGINT      NULL,
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by         BIGINT      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY `uk_execution_plan_dependency` (`plan_id`, `depends_on_plan_id`),
  KEY `idx_execution_plan_dependency_project` (`project_id`, `plan_id`),
  KEY `idx_execution_plan_dependency_upstream` (`project_id`, `depends_on_plan_id`),
  CONSTRAINT `fk_execution_plan_dependency_plan` FOREIGN KEY (`plan_id`) REFERENCES `execution_plan` (id),
  CONSTRAINT `fk_execution_plan_dependency_upstream` FOREIGN KEY (`depends_on_plan_id`) REFERENCES `execution_plan` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划依赖（前置 -> 后置 DAG 边）';

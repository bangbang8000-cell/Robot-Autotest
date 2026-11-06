-- V9_1 计划组（ORCH-01，v2.1）
--
-- 为什么需要「组」而不是又一个 DAG：
--   `execution_plan_dependency`（V6_2）与 `PlanDependencyGraph` 已经把图能力做完了，
--   缺的是「把一批计划当整体来编排」——用户现在只能逐条计划手工配依赖，配 10 条要点 10 次。
--   组只负责「成员 + 顺序」，边仍落在 `execution_plan_dependency`，不新增第二套图。
--
-- 口径：
--   1. 成员的 `sort_order` 是链式顺序：组内相邻两条，后一条依赖前一条（n 个成员 → n-1 条边）。
--   2. UNIQUE(group_id, plan_id) 保证同一计划在同组内只出现一次（重复加入幂等）。
--   3. 一个计划允许出现在多个组里 —— 组是编排视角，不是归属关系；冲突由成环检测兜底。
--   4. 组删除级联删成员（item），但**不删**已经生成的 `execution_plan_dependency` 边：
--      边一旦生成就是计划自己的配置，删组不应连带改计划行为。
--   5. 审计四列与其他业务表一致（V8_6 约定）。

CREATE TABLE `plan_group` (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  project_id  BIGINT       NOT NULL,
  name        VARCHAR(128) NOT NULL,
  description VARCHAR(512) NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by  BIGINT       NULL,
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by  BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY `uk_plan_group_name` (`project_id`, `name`),
  KEY `idx_plan_group_project` (`project_id`),
  CONSTRAINT `fk_plan_group_project` FOREIGN KEY (`project_id`) REFERENCES `project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划组（v2.1 ORCH-01）';

CREATE TABLE `plan_group_item` (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  project_id BIGINT      NOT NULL,
  group_id   BIGINT      NOT NULL,
  plan_id    BIGINT      NOT NULL,
  sort_order INT         NOT NULL DEFAULT 0 COMMENT '链式顺序，升序；相邻两条建成一条依赖边',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by BIGINT      NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY `uk_plan_group_item` (`group_id`, `plan_id`),
  KEY `idx_plan_group_item_order` (`group_id`, `sort_order`),
  KEY `idx_plan_group_item_project` (`project_id`, `plan_id`),
  CONSTRAINT `fk_plan_group_item_group` FOREIGN KEY (`group_id`) REFERENCES `plan_group` (id)
    ON DELETE CASCADE,
  CONSTRAINT `fk_plan_group_item_plan` FOREIGN KEY (`plan_id`) REFERENCES `execution_plan` (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划组成员（组内顺序）';

-- V9_2 报告模板（TPLX-01，v2.1）
--
-- 为什么需要模板：
--   对外报告的字段白名单（C5）是**死的**，但每个项目想看的字段不一样：硬件组只看
--   SW_BUILD + DUT + 通过率，管理层只想看结论与趋势。现在只能全字段导出，然后人工删列。
--   模板把「选哪些字段、什么顺序、标题叫什么」变成可保存的配置，导出时按它渲染。
--
-- 口径：
--   1. project_id = 0 表示**平台级内置模板**（与 V2_5 编辑器关键词同一套约定）。
--      本版平台级模板只读：改一处会影响所有项目的导出，不给改；项目要改就复制一份。
--   2. 模板字段只能取自 C5 白名单（ReportFieldWhitelist），校验在服务层；
--      本表不建白名单外键（白名单是代码常量，不是表）。
--   3. UNIQUE(project_id, name)：同名模板在同一 scope 内唯一；跨 scope 允许同名。
--   4. version 每次 update 自增，供 TPLX-02 的快照回指「冻结时用的是哪一版模板」。
--   5. 字段删改走「全量替换」（先删后插），故 field 表不保留历史版本；
--      历史可追溯性由报告快照自己承担（快照里存的是渲染结果 + template_version）。
--   6. 审计四列与其他业务表一致（V8_6 约定）。

CREATE TABLE `report_template` (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  project_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '0=平台级（内置只读），>0=项目私有',
  name        VARCHAR(128) NOT NULL,
  description VARCHAR(512) NULL,
  version     INT          NOT NULL DEFAULT 1 COMMENT '每次更新自增，供报告快照回指',
  is_default  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '项目默认模板；平台级恒为 1',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by  BIGINT       NULL,
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by  BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY `uk_report_template_name` (`project_id`, `name`),
  KEY `idx_report_template_project` (`project_id`),
  KEY `idx_report_template_default` (`project_id`, `is_default`),
  CONSTRAINT `ck_report_template_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告模板（v2.1 TPLX-01）';

CREATE TABLE `report_template_field` (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  project_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '与所属模板同 scope，便于租户条件直查',
  template_id    BIGINT       NOT NULL,
  field_key      VARCHAR(64)  NOT NULL COMMENT '取 C5 白名单 key，如 passRate',
  sort_order     INT          NOT NULL DEFAULT 0 COMMENT '升序即导出顺序',
  title_override VARCHAR(128) NULL COMMENT '自定义列名；空则用默认标题',
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by     BIGINT       NULL,
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by     BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY `uk_report_template_field` (`template_id`, `field_key`),
  KEY `idx_report_template_field_order` (`template_id`, `sort_order`),
  KEY `idx_report_template_field_project` (`project_id`),
  CONSTRAINT `fk_report_template_field_template` FOREIGN KEY (`template_id`)
    REFERENCES `report_template` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告模板字段（顺序 + 自定义标题）';

-- 平台内置 DEFAULT 模板：C5 白名单全字段，顺序即白名单声明顺序。
-- 没有它的话「未指定模板」就没有回落值，TPLX-02 的「未指定回落 DEFAULT」无从谈起。
INSERT INTO `report_template` (`project_id`, `name`, `description`, `version`, `is_default`)
VALUES (0, 'DEFAULT', '平台内置：C5 白名单全部字段，默认顺序', 1, 1);

SET @default_template_id := (
  SELECT `id` FROM `report_template` WHERE `project_id` = 0 AND `name` = 'DEFAULT'
);

INSERT INTO `report_template_field` (`project_id`, `template_id`, `field_key`, `sort_order`, `title_override`)
VALUES
  (0, @default_template_id, 'reportId',            0,  NULL),
  (0, @default_template_id, 'title',               1,  NULL),
  (0, @default_template_id, 'version',             2,  NULL),
  (0, @default_template_id, 'frozenAt',            3,  NULL),
  (0, @default_template_id, 'conclusion',          4,  NULL),
  (0, @default_template_id, 'passCount',           5,  NULL),
  (0, @default_template_id, 'failCount',           6,  NULL),
  (0, @default_template_id, 'skipCount',           7,  NULL),
  (0, @default_template_id, 'passRate',            8,  NULL),
  (0, @default_template_id, 'caseTotal',           9,  NULL),
  (0, @default_template_id, 'caseCovered',         10, NULL),
  (0, @default_template_id, 'coverageRate',        11, NULL),
  (0, @default_template_id, 'failureDistribution', 12, NULL),
  (0, @default_template_id, 'trend',               13, NULL),
  (0, @default_template_id, 'projectName',         14, NULL),
  (0, @default_template_id, 'swBuild',             15, NULL),
  (0, @default_template_id, 'caseKind',            16, NULL),
  (0, @default_template_id, 'startedAt',           17, NULL),
  (0, @default_template_id, 'finishedAt',          18, NULL),
  (0, @default_template_id, 'disclaimer',          19, NULL),
  (0, @default_template_id, 'watermark',           20, NULL);

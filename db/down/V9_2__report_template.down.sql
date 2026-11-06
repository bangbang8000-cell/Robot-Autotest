-- V9_2 回退：先删字段（有外键），再删模板。
-- 平台级 DEFAULT 模板一并删除：它是本迁移插入的数据，回退后 TPLX-02 的回落逻辑应随之失效。

DROP TABLE IF EXISTS `report_template_field`;
DROP TABLE IF EXISTS `report_template`;

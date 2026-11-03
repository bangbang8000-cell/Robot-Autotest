-- =============================================================================
-- V1__baseline.down.sql  ·  手写回滚（Flyway 社区版无 undo，ADR-0003）
-- 归属 WS-04 ｜ 对应 FR-v0.1-13 ｜ 执行方式：make db-undo
--
-- 只删 38 张业务表，不删 flyway_schema_history（由 Flyway 自己管理），
-- 所以回滚后 SHOW TABLES 的结果应为 1 张（历史表）。
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `export_record`;
DROP TABLE IF EXISTS `retention_policy`;
DROP TABLE IF EXISTS `system_setting`;
DROP TABLE IF EXISTS `notification`;
DROP TABLE IF EXISTS `audit_log`;

DROP TABLE IF EXISTS `schedule_trigger_log`;
DROP TABLE IF EXISTS `schedule_dependency`;
DROP TABLE IF EXISTS `schedule`;

DROP TABLE IF EXISTS `run_artifact`;
DROP TABLE IF EXISTS `keyword_result`;
DROP TABLE IF EXISTS `test_result`;
DROP TABLE IF EXISTS `run`;
DROP TABLE IF EXISTS `execution_event`;
DROP TABLE IF EXISTS `resource_lease`;
DROP TABLE IF EXISTS `execution_queue`;
DROP TABLE IF EXISTS `execution_job`;

DROP TABLE IF EXISTS `case_requirement`;
DROP TABLE IF EXISTS `robot_file_version`;
DROP TABLE IF EXISTS `robot_file`;
DROP TABLE IF EXISTS `test_set_item`;
DROP TABLE IF EXISTS `test_set`;
DROP TABLE IF EXISTS `test_case_step`;
DROP TABLE IF EXISTS `test_case`;

DROP TABLE IF EXISTS `topology_node`;
DROP TABLE IF EXISTS `env_profile`;
DROP TABLE IF EXISTS `device_check`;
DROP TABLE IF EXISTS `credential`;
DROP TABLE IF EXISTS `link`;
DROP TABLE IF EXISTS `device_port`;
DROP TABLE IF EXISTS `device`;

DROP TABLE IF EXISTS `api_token`;
DROP TABLE IF EXISTS `project_member`;
DROP TABLE IF EXISTS `user_role`;
DROP TABLE IF EXISTS `role`;
DROP TABLE IF EXISTS `app_user`;

DROP TABLE IF EXISTS `project_label`;
DROP TABLE IF EXISTS `project_setting`;
DROP TABLE IF EXISTS `project`;

SET FOREIGN_KEY_CHECKS = 1;

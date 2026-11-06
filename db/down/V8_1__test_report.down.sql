-- W2 回退：只删本版新建的三张表，不触碰任何既有业务表。
-- 与 V7_7/V7_8 的「空操作 down」不同：这三张表由 V8_1 独占创建，删除是安全的。
DROP TABLE IF EXISTS `test_report_audit`;
DROP TABLE IF EXISTS `test_report_export`;
DROP TABLE IF EXISTS `test_report`;

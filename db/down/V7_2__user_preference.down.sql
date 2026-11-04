-- V7_2__user_preference.down.sql  ·  回滚 UX-01 的偏好表
-- 只删表：偏好是纯展示续存数据，删掉只影响"换设备记住主题"，不影响任何业务数据。
DROP TABLE IF EXISTS `user_preference`;

-- V7_2__user_preference.sql  ·  v0.2 用户偏好（UX-01 / FR-v0.2-33）
-- 归属 UX-01 ｜ 对应 DEV-PLAN §10.3 步骤 6
--
-- 为什么单开一张表而不是塞进 app_user 加列：
--   偏好会越加越多（主题、语言、每页条数、默认项目…），每加一项就 ALTER 一次用户主表，
--   既影响登录路径的查询宽度，也让"用户表"承担了它不该承担的东西。KV 表加一项只是插一行。
--
-- 为什么带 pref_key 唯一键而不是一行存 JSON：
--   并发改不同偏好（本机改主题、另一处改语言）不会互相覆盖；改一项只写一行。
--
-- 值一律走白名单校验（见 UserPreferenceService），这张表不是通用 KV 存储。
CREATE TABLE `user_preference` (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  pref_key   VARCHAR(64)  NOT NULL COMMENT '偏好键，只接受白名单内的值',
  pref_value VARCHAR(255) NOT NULL COMMENT '偏好值，长度上限 255，不允许存凭据',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by BIGINT       NULL COMMENT '创建人；偏好表里恒等于 user_id，保留是为了与全库审计规范一致',
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_preference (user_id, pref_key),
  CONSTRAINT fk_user_preference_user FOREIGN KEY (user_id) REFERENCES `app_user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户偏好（跨设备一致）；删用户自动清理';

-- V7_6 执行域：运行记录补录 metadata（v1.0 HRD-02 / run:metadata-patch）
-- 版本号按现有最高位 V7_5 顺延：Flyway 只要求递增，补插低位会触发 out-of-order 校验失败。
--
-- 为什么单开 KV 表而不是给 run 加 JSON 列：
--   metadata 是对外报告口径（RUN_ID / CASE_ID / DUT / LINK / SW_BUILD …），补录按项进行，
--   KV 表能精确记「哪项从什么改成什么」，审计摘要也就能落到字段级；JSON 列无法在 SQL 层
--   保证键唯一，且并发补录不同键会互相覆盖。
CREATE TABLE `run_metadata` (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  project_id BIGINT       NOT NULL,
  run_id     VARCHAR(64)  NOT NULL COMMENT 'run.run_id，三方关联唯一键',
  meta_key   VARCHAR(64)  NOT NULL COMMENT '元数据键，服务层限 [A-Z][A-Z0-9_]{0,39}',
  meta_value VARCHAR(256) NOT NULL COMMENT '元数据值，服务层限 200 字符，不允许存凭据',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by BIGINT       NULL,
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_run_metadata_run_key (run_id, meta_key),
  KEY idx_run_metadata_project (project_id, run_id),
  CONSTRAINT fk_run_metadata_run FOREIGN KEY (run_id) REFERENCES `run` (run_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行记录补录 metadata（键值对，逐项可审计）';

-- V5_1 报告域字段（RPT-01/RPT-07）
-- 口径见 docs/release/v0.5-report/DEV-PLAN.md §2.2、§3。
-- 说明：
--   1. run.started_at 已由 V1 基线建立，本脚本不重复新增；metadata.STARTED_AT 解析后写回该列。
--   2. run.operator 存 metadata.OPERATOR（≤40 字符）；既有 operator_name 保留执行人账号字段语义，两者不混用。
--   3. metadata_json 存完整的十项键值，避免拆列后丢失 CASE_ID / QA_PROJECT 等未单列字段，供报告接口原样回传。
--   4. exportable / metadata_missing 在解析入库时固化，不在导出时重新判定。

ALTER TABLE `run`
  ADD COLUMN `dut` VARCHAR(40) NULL COMMENT 'metadata.DUT：被测设备' AFTER `operator_name`,
  ADD COLUMN `link` VARCHAR(40) NULL COMMENT 'metadata.LINK：被测链路（两端都写）' AFTER `dut`,
  ADD COLUMN `sw_build` VARCHAR(40) NULL COMMENT 'metadata.SW_BUILD：被测环境软件版本' AFTER `link`,
  ADD COLUMN `baseline` VARCHAR(40) NULL COMMENT 'metadata.BASELINE：对照基线量级' AFTER `sw_build`,
  ADD COLUMN `operator` VARCHAR(40) NULL COMMENT 'metadata.OPERATOR：执行人' AFTER `baseline`,
  ADD COLUMN `case_kind` VARCHAR(20) NULL COMMENT 'metadata.CASE_KIND：用例类型' AFTER `operator`,
  ADD COLUMN `metadata_json` TEXT NULL COMMENT '完整 metadata 键值（JSON），供报告接口原样回传' AFTER `case_kind`,
  ADD COLUMN `metadata_missing` VARCHAR(200) NULL COMMENT '缺失的十项键名，逗号分隔；NULL 表示不缺失' AFTER `metadata_json`,
  ADD COLUMN `exportable` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=可对外交付（十项齐全且无超长）' AFTER `metadata_missing`,
  ADD COLUMN `pass_count` INT NOT NULL DEFAULT 0 COMMENT '解析得到的 PASS 用例数' AFTER `exportable`,
  ADD COLUMN `fail_count` INT NOT NULL DEFAULT 0 COMMENT '解析得到的 FAIL 用例数' AFTER `pass_count`,
  ADD COLUMN `skip_count` INT NOT NULL DEFAULT 0 COMMENT '解析得到的 SKIP 用例数' AFTER `fail_count`;

ALTER TABLE `test_result`
  ADD COLUMN `case_key` VARCHAR(160) NOT NULL DEFAULT '' COMMENT 'suitePath.caseName：跨 run 对比的连接键' AFTER `case_id`,
  ADD COLUMN `log_offset` BIGINT NULL COMMENT '日志文件行偏移（RPT-02 回填）' AFTER `duration_ms`,
  ADD COLUMN `failure_kind` VARCHAR(16) NULL COMMENT 'assert/env/infra：与 v0.4 失败分类对齐' AFTER `failure_msg`;

ALTER TABLE `keyword_result`
  ADD COLUMN `arg_count` SMALLINT NOT NULL DEFAULT 0 COMMENT '关键字参数个数' AFTER `depth`,
  ADD COLUMN `log_span` VARCHAR(64) NULL COMMENT '日志行区间 start:end，日志不重复存' AFTER `arg_count`;

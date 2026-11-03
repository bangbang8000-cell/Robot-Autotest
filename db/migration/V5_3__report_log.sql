-- V5_3 报告日志定位（RPT-02）
-- 口径见 docs/release/v0.5-report/DEV-PLAN.md §2.3「大字段策略」：
--   日志全文只留在 artifacts/<run_id>/output.xml（XML 内 <msg>），数据库只存偏移，
--   报告页按 offset/span 运行时从文件流式读取，不把整文件读进内存。
--
-- 偏移语义：把 output.xml 中所有 <msg> 元素按文档顺序从 0 编号，
--   log_offset = 该节点首条消息的序号；
--   log_span   = 'start:end'，左闭右开（end 为最后一条消息序号 + 1）。
--
-- 说明：
--   1. test_result.log_offset 已在 V5_1 建立，本脚本补 case 级 log_span。
--   2. keyword_result.log_span 已在 V5_1 建立，本脚本补 keyword 级 log_offset。
--   3. 不新增 message 正文列：正文留在 output.xml，避免单 run 库体积超过 1 MB 目标。

ALTER TABLE `test_result`
  ADD COLUMN `log_span` VARCHAR(64) NULL COMMENT '用例日志序号区间 start:end（左闭右开）' AFTER `log_offset`;

ALTER TABLE `keyword_result`
  ADD COLUMN `log_offset` BIGINT NULL COMMENT '关键字首条消息在 output.xml 中的序号' AFTER `arg_count`;

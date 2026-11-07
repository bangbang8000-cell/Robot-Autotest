-- V10_2__case_key_strip_run_root.down.sql
-- 回填不可逆：原 case_key 前缀含当次运行的随机 RUN_ID（RUN-yyyyMMddHHmmss-<hex>），
-- 历史值已不可重建。此处刻意不做任何数据改写，避免用假前缀污染趋势数据。
-- 如需恢复旧口径，只能回退代码（ReportRepositoryJdbc.rootBasePath）并对该 run 重新解析入库：
--   POST /api/v1/reports/parse（同一 run 幂等，会先清空 test_result 再重写）。
SELECT 1;

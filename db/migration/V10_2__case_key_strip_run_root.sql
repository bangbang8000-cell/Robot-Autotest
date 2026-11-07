-- V10_2__case_key_strip_run_root.sql  ·  v2.0 收口 G-B8-1：case_key 剥离根套件 run-<RUN_ID>
-- 归属 W0.1 ｜ 证据 docs/evidence/v2.0/w0-1-case-key/
--
-- 背景：执行侧为了让 RF 报告标题可读，把根套件命名为 `run-<RUN_ID>`
-- （RunExecutionService / PabotCommandBuilder 均传 "run-" + runId）。入库时该名字被拼进
-- test_result.case_key，导致同一条用例每次运行的 key 都不同：
--   run-RUN-20261107151146-abcdef.B8 用例甲
--   run-RUN-20261107160000-abcdef.B8 用例甲
-- 后果：趋势（TrendRepositoryJdbc）、跨 run 对比、覆盖率精确匹配全部失效（G-B8-1）。
-- 修复：入库时剥离根套件（ReportRepositoryJdbc.rootBasePath）；本迁移回填历史数据。
-- 回填后 case_key = `套件路径.用例名`，跨 run 稳定。
--
-- 匹配口径：runId 格式固定 RUN-yyyyMMddHHmmss-<hex>（RunIdGenerator），
-- 因此历史 key 一定以 `run-RUN-<14 位数字>-<小写 hex>.` 开头。
UPDATE test_result
   SET case_key = SUBSTRING(case_key, LOCATE('.', case_key) + 1)
 WHERE case_key REGEXP '^run-RUN-[0-9]{14}-[0-9a-f]+[.]';

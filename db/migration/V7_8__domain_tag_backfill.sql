-- V7_8__domain_tag_backfill.sql · AST-04 案 A-②「按一级分类映射打标」
--
-- 归属 WBS #15 ｜ 对应 `docs/plan/P1-产品口径两案清单-20261106.md` §1
--
-- 台账没有「技术域」列，226 条里实测 211 条 domain_tag 为空（2026-11-06 只读勘查，
-- 见 docs/evidence/v1.0/p1-closeout/inspect_db.py）。一级分类落在 `case_kind`，共 13 类。
-- 映射规则由 AI 按语义制定并在此显式列出（**属推断，不是台账事实**）：
--
--   基本功能测试              → ethernet   基础网络转发 / 连通
--   性能测试 / 压力 / 混流     → rdma       RoCE 吞吐与压力（AIDC 网卡主场景）
--   稳定性测试                → qos        长时间稳态与限速
--   网卡专用工具测试          → offload    卸载与工具链
--   结构 / 散热 / Thermal / SI / SIV / EMC / REL → hardware  硬件与信号类
--   其它 / 空                 → general    显式兜底，保证非空率 100%
--
-- 两条纪律：
-- 1. **只填空值**：已有 roce(10) / storage(5) 是人工标注，不得覆盖；
-- 2. **幂等**：重复执行命中 0 行，Flyway 之外手工重跑也安全。
UPDATE `test_case`
SET `domain_tag` = CASE
    WHEN `case_kind` = '基本功能测试' THEN 'ethernet'
    WHEN `case_kind` IN ('性能测试', '压力测试', '混流测试') THEN 'rdma'
    WHEN `case_kind` = '稳定性测试' THEN 'qos'
    WHEN `case_kind` = '网卡专用工具测试' THEN 'offload'
    WHEN `case_kind` IN ('结构测试', '散热测试', 'Thermal', 'SI测试', 'SIV', 'EMC', 'REL')
        THEN 'hardware'
    ELSE 'general'
END
WHERE `domain_tag` IS NULL OR `domain_tag` = '';

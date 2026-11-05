-- V7_5 用例域：执行安全级（v0.2 CAS / 门禁 G2-3 / ADR-0013）
--
-- 值域：L0 只读 / L1 可逆写 / L2 高危不可逆 / LX 未判定。
-- 默认 LX：导入台账没有安全级列即"未判定"，执行入口按 L2 同等对待（ADR-0013 §2），
-- 人工补判后由批量动作 setSafetyLevel 改档。

ALTER TABLE `test_case`
  ADD COLUMN `safety_level` VARCHAR(8) NOT NULL DEFAULT 'LX'
    COMMENT '执行安全级：L0 只读 / L1 可逆写 / L2 高危 / LX 未判定（ADR-0013）'
    AFTER `priority`;

ALTER TABLE `test_case` ADD INDEX idx_test_case_safety (project_id, safety_level);

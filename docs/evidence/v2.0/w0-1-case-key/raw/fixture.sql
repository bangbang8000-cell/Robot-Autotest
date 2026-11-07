DROP TABLE IF EXISTS w0_1_fixture;
CREATE TABLE w0_1_fixture (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  case_key VARCHAR(512) NOT NULL
);
-- 4 行：两条来自不同 run 的同一用例（带平台根套件前缀）、一条带子套件路径、一条已无前缀的旧数据
INSERT INTO w0_1_fixture (case_key) VALUES
  ('run-RUN-20261106120000-a1b2c3.B8 用例甲'),
  ('run-RUN-20261106123000-d4e5f6.B8 用例甲'),
  ('run-RUN-20261106123000-d4e5f6.SuiteB.B8 用例乙'),
  ('B8 用例丙');

SELECT '--- BEFORE ---' AS step;
SELECT id, case_key FROM w0_1_fixture ORDER BY id;

SELECT '--- DRY-RUN: 将被改写的行数 ---' AS step;
SELECT COUNT(*) AS would_change
FROM w0_1_fixture
WHERE case_key REGEXP '^run-RUN-[0-9]{14}-[0-9a-f]+[.]';

SELECT '--- APPLY (V10_2 的 UPDATE 原文) ---' AS step;
UPDATE w0_1_fixture
SET case_key = SUBSTRING(case_key, LOCATE('.', case_key) + 1)
WHERE case_key REGEXP '^run-RUN-[0-9]{14}-[0-9a-f]+[.]';
SELECT ROW_COUNT() AS rows_changed;

SELECT '--- AFTER ---' AS step;
SELECT id, case_key FROM w0_1_fixture ORDER BY id;

SELECT '--- 跨 run 配对检查：同一用例的 case_key 是否唯一 ---' AS step;
SELECT case_key, COUNT(*) AS runs
FROM w0_1_fixture
GROUP BY case_key
ORDER BY case_key;

DROP TABLE w0_1_fixture;

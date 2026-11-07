# W0.1 · G-B8-1：`case_key` 剥离平台根套件（P0 数据缺陷）

> 对应 `docs/plan/中期开发功能规划-20261107.md` **W0.1 / N1**。
> 取证日期 2026-11-07；MySQL 8.0.46（`robot_dev` / `robot_it`）、Flyway 10.2、后端 JUnit 5 + AssertJ。
> **结论：入库路径已修（同一用例跨 run 的 `case_key` 不再带 `run-<RUN_ID>` 前缀）；历史回填脚本齐备，
> 但两个库当前均无历史脏数据（dev 60 行命中 0、it 0 行），故回填在真实库上是 no-op，
> 效果由合成夹具证明（dry-run 3 行 = 实更 3 行）。**

## 1. 判据与结论

| 判据 | 结论 | 依据（`raw/`） |
|---|---|---|
| 入库剥离根套件段 | **PASS** | `ReportRepositoryJdbc.rootBasePath()`：仅当 `rootName.equals("run-" + runId)` 时返回 `""`；单测 `ReportRepositoryCaseKeyTest` **4/4**（`unit-tests.txt`） |
| 一次性回填迁移（带 down） | **PASS（真实库 no-op）** | `V10_2__case_key_strip_run_root.sql` / `...down.sql`（`V10_2.up.sql`、`V10_2.down.sql`）；`robot_dev` 60 行、`legacy_rows=0`（`casekey-audit-dev.txt`） |
| 回填 dry-run 条数 = 实更条数 | **PASS 3/3** | 合成夹具：DRY-RUN `would_change=3`，APPLY 后 `rows_changed=3`（`fixture-run.txt`） |
| 回填后同一用例跨 run 可配对 | **PASS** | AFTER：两条来自不同 run 的 `B8 用例甲` 合并为同一 `case_key`（`GROUP BY case_key` 记 `runs=2`）（`fixture-run.txt`） |
| 不改用户自建的根套件名 | **PASS** | `rootBasePath` 在 runId 为空或名称不匹配时原样返回，不做「模糊剥离」 |

## 2. 改了什么

| 文件 | 变更 |
|---|---|
| `backend/.../infrastructure/ReportRepositoryJdbc.java` | `insertSuite` 递归时把根套件当虚拟路径（`rootBasePath` 为空串）；`case_key` 由 `path + "." + test.name` 组成，不再包含平台生成的根套件名 |
| `db/migration/V10_2__case_key_strip_run_root.sql` | 一次性回填：`SUBSTRING(case_key, LOCATE('.', case_key) + 1)`，条件 `REGEXP '^run-RUN-<14位时间戳>-<hex>[.]'` |
| `db/down/V10_2__case_key_strip_run_root.down.sql` | 回填不可逆（旧前缀已丢），只留 `SELECT 1` + 说明：需要旧形态时用 `POST /api/v1/reports/parse` 重新入库 |
| `backend/src/test/.../ReportRepositoryCaseKeyTest.java` | 4 条纯函数用例：正常剥离、runId 为空不剥离、名称不匹配不剥离、子套件路径拼接 |

根因链路：`RunExecutionService` 用 `--name run-<RUN_ID>` 建根套件 →
`ReportRepositoryJdbc.insertSuite` 把根套件名拼进 `case_key` → 同一用例在不同 run 得到不同 `case_key`
→ `compare` 配不上、`trend.points` 永远 1 个点。

## 3. 两个必须记住的坑

### G-W0.1-1 回填只认平台前缀，不做通用剥离
正则锁死 `run-RUN-[0-9]{14}-[0-9a-f]+` + 紧邻 `.`。用户自己在 RF 里建的 `run-start.用例` 这类套件名
不会被误伤 —— 剥离错了就是**破坏性数据变更**，宁可漏不可错。

### G-W0.1-2 down 不能真回滚
旧前缀里有 runId，但历史 run 被清理后无法重建「哪条结果来自哪个 run」，所以 down 只做提示。
真要恢复只能重新解析 `output.xml`。这也是本次先备份库再回填的原因。

## 4. 复现

```bash
# 单测（4 条）
cd ~/robot-autotest/backend && mvn -o test -Dtest=ReportRepositoryCaseKeyTest

# 回填夹具演示（自带建表/清理，不动业务表）
bash /tmp/dq.sh   # 见 raw/fixture.sql，直接跑：mysql ... < raw/fixture.sql
```

真实库读数（2026-11-07）：

```
total_rows = 60, legacy_rows = 0      # robot_dev
robot_it  ：0 行（门禁会 TRUNCATE）
```

> 结论：**当前不需要跑回填**；脚本保留，用于将来导入他环境的历史库。

# UX-02b / EXE-09 / ENV-06 证据汇总（2026-11-04 22:15）

本目录日志由 `/tmp/final_verify_ux02b_20261104.sh` 一次跑出，未做任何筛选重跑。

| 日志 | 实测结果 |
|---|---|
| `mvn-unit.log` | `Tests run: 476, Failures: 0, Errors: 0` — `BUILD SUCCESS` |
| `mvn-it.log` | `Tests run: 25, Failures: 0, Errors: 0` — `BUILD SUCCESS`（含 `GenericBatchIT` 3/3） |
| `fe-test.log` | 3 个测试文件 / **21 tests passed**（`useSelection` 8、`useBatchAction` 5、`format` 8） |
| `fe-lint.log` | `eslint .` exit 0，无告警输出 |
| `fe-build.log` | `vue-tsc --noEmit && vite build` exit 0，`✓ built in 3.60s` |
| `docs-gate.log` | 7 版本 / **FAIL 0 / WARN 0** |
| `repo-lint.log` | `[lint] PASS`（含 secret-scan） |
| `i18n.log` | 键集相等 476/476、硬编码中文 0、同值漏译 0、键引用完整、23 枚举全覆盖 |

## 破坏性验证（防止「守门假绿」）

1. **`DownScriptGuardTest`**（`/tmp/verify_down_guard_20261104.py`）：
   - 原始 `V5_1__report_fields.down.sql` 709 字节；
   - 注入真违规 `ALTER TABLE \`run\` DROP COLUMN IF EXISTS \`__guard_probe\`;`
     → `mvn -Dtest=DownScriptGuardTest test` **exit 1** 且守门命中；
   - 还原后字节**完全一致**，重跑 **exit 0**。
   - 另修一处「守门自咬」：down 脚本注释里写着「目标库不支持 DROP COLUMN IF EXISTS」，
     初版守门把注释当违规 SQL 命中；改为先剥离 `--` 注释再检查有效语句。
2. **`RunIdGeneratorTest`**：单独连跑 **10 次**，每次 `Tests run: 7, Failures: 0`、`exit 0`。
   旧实现同条件约 2.9% 概率 flaky，10 次约 25% 至少命中一次；本版 0 命中。
3. **`GenericBatchIT`**：失败项不是 mock 造的，而是复用 PRJ-03 的引用保护
   （被引用的平台资产硬删 `CONFLICT`），真库验证「部分成功 200 + 失败项不写入」。

## 未验 / 边界

- 报告页批量未接入（契约不兼容），见 `TEST-PLAN` §13.12。
- `CREDENTIAL` 批量删除由 Spring 装配覆盖，IT 未单独造凭据（需真实加密主密钥）；未做端到端实跑。
- 前端两页批量**未做浏览器走查**（需人在场），目前证据到 vitest + 类型检查 + 构建为止。

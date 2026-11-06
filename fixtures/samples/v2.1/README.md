# 示例用例集（v2.1 / SMPL-01）

> 归属 **SMPL-01**。本目录是**平台自身的回归底座**，不是业务用例。

## 三条硬边界

| 边界 | 含义 | 为什么 |
|---|---|---|
| **不映射业务** | 用例名里没有设备、链路、协议名词，只验证断言与状态 | 业务用例会随产品变，底座一变，回归基线就失去可比性 |
| **不碰 DUT** | 无 SSH、无 ping、无 iperf3、无任何设备地址 | ADR-0013 禁跑令虽已按 2026-11-06 拍板收窄到「真实 DUT / 交换机」，但底座本就不需要外部设备 |
| **不联网** | 无 HTTP、无 pip、无域名 | 离线机器、CI、笔记本都能跑出同一结果 |

## 覆盖的状态

RF 与 pytest 各覆盖自己能力范围内的状态，合起来**五态齐全**（`CaseStatus` 六态里 XPASS 未单独造样例，见「已知缺口」）。

| 状态 | RF 样例 | pytest 样例 | 平台应记为 |
|---|---|---|---|
| PASS | `01_pass.robot`（2 条） | `test_pass_equal` / `test_pass_container` | `PASS` |
| FAIL | `02_fail.robot`（2 条，断言失败 + 预期异常未抛） | `test_fail_assertion` | `FAIL` |
| SKIP | `03_skip.robot`（2 条，无条件 Skip + IF 分支内 Skip） | `test_skip_explicit` | `SKIP` |
| XFAIL | —（RF 无对应语义） | `test_xfail_expected` | `XFAIL` |
| ERROR | —（RF 无对应语义） | `test_error_fixture`（fixture 抛错） | `ERROR` |

`02_fail.robot` 与 `test_fail_assertion` 是**故意失败**的，不得当作产品缺陷；失败信息形状固定（`3 != 5`），专门给失败聚类的归一化做对照。

## metadata 口径

RF 侧按本仓既定规矩写在 suite 级 `Metadata`（`MetadataGate.REQUIRED` 十项），键名全大写、值 ≤ 40 字符、不含凭据。可变项一律走环境变量，便于平台注入：

```
%{RUN_ID=LOCAL-SMPL-0001}   %{SW_BUILD=v2.1-sample}   %{OPERATOR=local}
```

pytest 侧没有 suite metadata 概念，用模块级 `SAMPLE_METADATA` 声明同一套键，键名与 RF 完全一致。

**已知缺口**：`PytestKernel.fromJson` 只读 `tests[].outcome`，尚未读取 metadata，所以 pytest 侧六必填**只声明、未进报告门禁**（RF 侧的十项已实测进 `output.xml`）。

## 跑法

```bash
# RF：先 dryrun 查语法（注意 dryrun 对语法错误退出码仍是 0，只能看输出里有没有 [ ERROR ]）
~/rf-stack/.venv/bin/robot --dryrun --outputdir /tmp/smpl-dry fixtures/samples/v2.1/robot

# RF：实跑（预期 2 passed / 2 failed / 2 skipped，整体退出码非 0 是正常的）
~/rf-stack/.venv/bin/robot --outputdir /tmp/smpl-run fixtures/samples/v2.1/robot

# pytest：venv 由 scripts/setup-pytest-venv.sh 建立
~/venvs/pytest-kernel/bin/python -m pytest fixtures/samples/v2.1/pytest -q \
    --json-report --json-report-file=/tmp/smpl-pytest/report.json
```

## 一份实测读数（2026-11-06）

| 侧 | 读数 | 状态分布 |
|---|---|---|
| RF 7.5.1 | `6 tests, 2 passed, 2 failed, 2 skipped` | PASS / FAIL / SKIP 各 2 |
| pytest 9.2.0 + json-report 1.5.0 | `2 passed, 1 failed, 1 skipped, 1 xfailed, 1 error` | 六条用例覆盖五态 |

两侧的原始输出与 `output.xml` / `pytest-report.json` 存在 `docs/evidence/v2.1/SMPL-01/`。

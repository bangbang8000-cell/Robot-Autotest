# SMPL-01 证据 · 示例用例集

- **执行时间**：2026-11-06 20:12（GMT+8）
- **执行机**：WSL Ubuntu-24.04（`~/robot-autotest` ext4 副本）
- **运行时**：Robot Framework 7.5.1（`~/rf-stack/.venv`）· pytest 9.2.0 + pytest-json-report 1.5.0 + pytest-xdist 3.8.0（`~/venvs/pytest-kernel`）
- **结论**：**通过**。RF 6 条用例 2 passed / 2 failed / 2 skipped；pytest 6 条用例覆盖 passed / failed / skipped / xfailed / error 五态；两侧 metadata 十项齐全。

## 1. 命令原文

```bash
cd ~/robot-autotest

# ① RF 语法自检（dryrun 的退出码对语法错误仍为 0，只能看输出里有没有 [ ERROR ]）
~/rf-stack/.venv/bin/robot --dryrun --outputdir /tmp/smpl01/dry2 fixtures/samples/v2.1/robot

# ② RF 实跑
~/rf-stack/.venv/bin/robot --outputdir /tmp/smpl01/run fixtures/samples/v2.1/robot

# ③ pytest 实跑（带 JSON 报告，与 PytestKernel 的主通道一致）
~/venvs/pytest-kernel/bin/python -m pytest fixtures/samples/v2.1/pytest -q \
    --json-report --json-report-file=/tmp/smpl01/pytest-report.json
```

## 2. 原始输出

**① dryrun `[ ERROR ]` 计数**

```
$ grep -c "\[ ERROR \]" /tmp/smpl01/dryrun.log
0
```

**② RF 实跑末尾**

```
Robot                                                                 | FAIL |
6 tests, 2 passed, 2 failed, 2 skipped
```

整体 FAIL 是**预期**的——`02_fail.robot` 里有两条故意失败的用例。

**③ pytest 实跑末尾**

```
ERROR fixtures/samples/v2.1/pytest/test_sample_states.py::test_error_fixture
1 failed, 2 passed, 1 skipped, 1 xfailed, 1 error in 0.02s
```

## 3. 判据逐条核对

| 判据 | 要求 | 实测 | 结论 |
|---|---|---|---|
| dryrun 无语法错误 | 输出中 `[ ERROR ]` 计数 = 0 | `0` | 通过 |
| 五态各 ≥ 1 | PASS / FAIL / SKIP / XFAIL / ERROR | RF：PASS 2 / FAIL 2 / SKIP 2；pytest：passed 2 / failed 1 / skipped 1 / xfailed 1 / error 1 | 通过 |
| metadata 六必填齐 | `CASE_ID/RUN_ID/QA_PROJECT/DUT/LINK/SW_BUILD` 全部非空 | 三个 RF suite 各 10 项齐全，变量替换生效 | 通过 |

**metadata 实测值**（从 `output.xml` 抽取，三个 suite 各一份）：

```
<meta name="CASE_ID">SMPL-RF-PASS</meta>      <meta name="RUN_ID">LOCAL-SMPL-0001</meta>
<meta name="QA_PROJECT">Robot-Autotest</meta> <meta name="DUT">none-synthetic</meta>
<meta name="LINK">loopback-local</meta>       <meta name="SW_BUILD">v2.1-sample</meta>
<meta name="BASELINE">sample-v2.1</meta>      <meta name="OPERATOR">local</meta>
<meta name="STARTED_AT">1970-01-01T00:00:00Z</meta>  <meta name="CASE_KIND">sample</meta>
```

`%{RUN_ID=LOCAL-SMPL-0001}` / `%{SW_BUILD=v2.1-sample}` / `%{OPERATOR=local}` 三个环境变量语法在 suite 级 Metadata 里**确认会被替换**（不是原样输出）。

## 4. 双内核状态映射实测

pytest 的 `pytest-report.json` 里 `tests[].outcome` 实际取值与 `CaseStatus.fromPytest` 的映射逐条对上：

| nodeid | outcome | `CaseStatus.fromPytest` |
|---|---|---|
| `test_pass_equal` | `passed` | `PASS` |
| `test_pass_container` | `passed` | `PASS` |
| `test_fail_assertion` | `failed` | `FAIL` |
| `test_skip_explicit` | `skipped` | `SKIP` |
| `test_xfail_expected` | `xfailed` | `XFAIL` |
| `test_error_fixture` | `error` | `ERROR` |

`summary` 字段：`{'passed': 2, 'failed': 1, 'skipped': 1, 'xfailed': 1, 'error': 1, 'total': 6}`。

## 5. 遗留

| # | 事项 | 影响 | 归属 |
|---|---|---|---|
| 1 | **`PytestKernel.fromJson` 不读 metadata**：只取 `nodeid` / `outcome` / `duration`，不读 `root.metadata`，因此 pytest 侧六必填只声明、未进 `MetadataGate` | pytest 项目的外部报告会因 metadata 缺失被 RPT-05 拒绝 | A2（真实 pytest 实跑时一并处理） |
| 2 | **XPASS 未造样例**：pytest 侧只有 xfail（strict），要产出 `xpassed` 需 `xfail_strict=false` 的另一条用例 | 六态里 XPASS 无实测样本 | 低优先；`CaseStatus.XPASS` 已被 `isFailure()` 计为失败，逻辑无需改 |
| 3 | RF 侧无 XFAIL / ERROR 语义 | 不是缺陷，是 RF 只为用例产出 PASS/FAIL/SKIP 三态 | — |

## 6. 文件

| 文件 | 内容 |
|---|---|
| `dryrun.log` | ① 的完整输出（`[ ERROR ]` 计数为 0 的依据） |
| `output.xml` | ② 的原生产物（含三个 suite 的十项 metadata，可复算门禁） |
| `pytest.log` | ③ 的完整输出 |
| `pytest-report.json` | ③ 的原生 JSON 报告（`tests[].outcome` 六态映射的依据） |

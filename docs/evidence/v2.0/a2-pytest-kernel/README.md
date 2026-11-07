# A2 证据 · 真实 pytest 内核实跑（deepseek-harness + 六态样本 + 规模样本）

- **RUN_ID**：`A2-PYTEST-20261107_1121`
- **执行时间**：2026-11-07 11:21–11:24（GMT+8）
- **结论**：**通过**。真实工程 `~/deepseek-harness` 收集 **254** 条、实跑 **245 passed / 9 skipped**；
  `--collect-only -q` 与 `--json-report` 的 nodeid **逐条 IDENTICAL（254 = 254，差集 0）**；
  六态（pass/fail/skip/xfail/xpass/error）各拿到 **1 条真实样本**；规模样本 `~/.hermes/hermes-agent`
  缺依赖如实记录、不伪造通过。

## 1. 环境隔离决策（不污染 pytest 白名单 venv）

`config/pytest-venv.yml` 的白名单只有 `pytest` / `pytest-json-report` / `pytest-xdist`，
**实测样本工程的依赖不装进 `~/venvs/pytest-kernel`**，另建专用解释器：

```bash
python3 -m venv ~/venvs/deepseek-harness
~/venvs/deepseek-harness/bin/pip install pytest pytest-json-report pytest-xdist \
    'pillow==12.3.0' 'pydantic>=2.12,<3'
# pytest 9.2.0；pip freeze 共 16 项（raw/pip-freeze.log）
```

`~/deepseek-harness` 的 SDK 是本地源码包，其 `deepseek-harness-runtime-bin` 是 `[tool.uv.sources]`
路径依赖（hatch 构建钩子会要求已注入的 runtime 可执行文件），**不做 `pip install -e`**；
改用 `PYTHONPATH` 指向两处源码目录（不改样本仓库一个字节）：

```bash
export PYTHONPATH=$HOME/deepseek-harness/python/sdk/src:$HOME/deepseek-harness/python/sdk-runtime/src
```

## 2. 缺依赖基线（如实清单，未伪造）

用白名单 venv `~/venvs/pytest-kernel`（不设 PYTHONPATH）跑同一工程：

```
23 tests collected, 14 errors in 0.25s
      6 No module named 'deepseek_harness'
      4 No module named 'pydantic'
      3 No module named 'deepseek_harness_runtime'
      1 No module named 'PIL'
```

原始日志 `raw/missing-deps-baseline.log`。这解释了 A1 之前的 `NOT-RUN`：**不是缺外部样本，是环境少一步**。

## 3. 真实执行（与 `PytestKernel.command` 同参数）

`PytestKernel.command` 的 argv 形如
`pytest --json-report --json-report-file=<artifacts>/kernel-pytest-0/pytest-report.json --junitxml=<artifacts>/kernel-pytest-0/junit.xml -q <suitePaths>`。
本证据按同一参数组合执行（cwd 即工程根，故不重复传路径）：

```bash
cd ~/deepseek-harness
timeout 120 $VENV/bin/python -m pytest --collect-only -q > raw/collect-only.log 2>&1
timeout 300 $VENV/bin/python -m pytest --json-report --json-report-file=raw/pytest-report.json \
  --junitxml=raw/junit.xml -q > raw/full-run.log 2>&1
```

原始输出（`raw/full-run.log` 末行）：

```
245 passed, 9 skipped in 30.30s
```

## 4. 判据逐条核对

| 判据（收口计划 A2） | 要求 | 实测 | 结论 |
|---|---|---|---|
| collect 条数逐条对得上 | 平台侧 / 本地 `--collect-only -q` 一致 | `raw/compare.txt`：collect 254 / json-report 254 / 仅 collect 有 0 / 仅报告有 0 / `RESULT: IDENTICAL` | 通过 |
| json-report + junitxml 各留 1 份原始产物 | 各 ≥1 份 | `raw/pytest-report.json`（188 KB）、`raw/junit.xml`（46 KB） | 通过 |
| 六态各 ≥1 条真实样本 | pass/fail/skip/xfail/xpass/error | `states/raw/pytest-report.json` 六种 outcome 全出现（见 §5） | 通过 |
| 状态映射与平台一致 | `CaseStatus.fromPytest` 六态不折叠 | 真实 outcome 字符串 `passed/failed/skipped/xfailed/xpassed/error` 与 `CaseStatus` 分支逐一对应 | 通过 |

**踩坑（已修）**：`compare.py` 最初用 `line.split(" ", 1)[0]` 截 nodeid，遇到带空格的参数化 id
（如 `[crash.docx-failed-7-engine process unavailable]`）会截断，报出 15/19 条假差异；
改为整行保留后差集归零。**参数化 id 含空格是 pytest 的合法形态**，任何按空格切 nodeid 的实现都会错。

## 5. 六态样本（真实执行，非构造 JSON）

`states/test_states.py` 是**被真实 pytest 执行的用例**，故意制造六种结局；
`states/raw/run.log` 末行：

```
1 failed, 1 passed, 1 skipped, 1 xfailed, 1 xpassed, 1 error in 0.03s
```

`states/raw/pytest-report.json` 逐条 outcome：

```
test_states.py::test_pass   -> passed
test_states.py::test_fail   -> failed
test_states.py::test_skip   -> skipped
test_states.py::test_xfail  -> xfailed
test_states.py::test_xpass  -> xpassed
test_states.py::test_error  -> error      # fixture setup 抛 RuntimeError
```

说明：PytestKernel 的 Java 归一路径由既有 `PytestKernelTest`（合成 JSON + junit 兜底）覆盖；
本证据补的是**真实 pytest 产出的六态字符串**，两者合起来才是「真实链路 + 映射正确」。

## 6. 规模样本（hermes-agent）

该工程依赖未安装，**只做 collect 层级取证，不推测条数、不伪造通过**。三份原始日志：

| 步骤 | 命令 | 实测读数 | 日志 |
|---|---|---|---|
| 依赖缺口扫描 | `python3 hermes_scan.py`（AST 抽顶层 import + `find_spec`） | `test_*.py` **5204** 个；逐条缺失模块清单 | `raw/hermes-missing-deps.log`（3.1 KB） |
| 全仓 collect | `timeout 480 python -m pytest --collect-only -q --continue-on-collection-errors` | **退出码 124，耗时 504s，480s 内未完成被超时杀死 → 不产出条数结论** | `raw/hermes-collect.log` |
| 有界子集 collect | `tests/` 前 200 个 `test_*.py`，`timeout 300` | **552 collected / 123 errors / 40.59s，退出码 1** | `raw/hermes-subset-collect.log`（183 KB） |

```
# ---- 命令: timeout 480 pytest --collect-only -q --continue-on-collection-errors
# ---- 退出码: 124  耗时: 504s
# ---- 结论: 480s 内未完成（超时被杀），不产出条数结论，不伪造

552 tests collected, 123 errors in 40.59s
```

结论口径：**全仓规模在当前机器上单次不可测完**（>8 分钟未收敛）；有界子集能给出可复现读数，
552/123 说明「collect 能在真实大仓上跑起来，但缺依赖会以 errors 呈现而不是静默丢弃」。
这一条不参与 A2 通过判定，只作规模参考。

## 7. 复跑

```bash
bash ~/robot-autotest/docs/evidence/v2.0/a2-pytest-kernel/run.sh
# 跳过规模样本：SKIP_SCALE=1 bash run.sh
```

## 8. 文件

| 文件 | 内容 |
|---|---|
| `run.sh` | 一键复跑（collect → 实跑 → 比对 → 六态 → 规模样本） |
| `compare.py` | nodeid 集合逐条对齐，退出码 0/1 |
| `raw/collect-only.log` | `--collect-only -q` 原始输出（254 条） |
| `raw/full-run.log` | 真实执行 stdout/stderr |
| `raw/pytest-report.json` / `raw/junit.xml` | 主通道 / 兜底通道原始产物 |
| `raw/compare.txt` | `IDENTICAL` 判定 |
| `raw/missing-deps-baseline.log` | 白名单 venv 下的缺依赖基线（23 collected / 14 errors） |
| `raw/hermes-collect.log` | 全仓 collect 尝试（RC=124 超时，未产出条数） |
| `raw/hermes-subset-collect.log` | 有界子集 collect 原始输出（552 collected / 123 errors） |
| `raw/hermes-missing-deps.log` | hermes-agent 依赖缺口扫描 |
| `hermes_scan.py` | 规模样本依赖扫描脚本（AST 抽 import + find_spec） |
| `states/` | 六态样本源码与原始产物 |

## 9. 遗留

- 样本工程未做 `pip install -e`，因此 SDK 的**打包元数据**（wheel 内容、`dsh` 入口）未验证；
  这不影响内核 collect/execute/parse 三条判据，但对外发布 SDK 时需另测。
- `~/venvs/deepseek-harness` 不在仓库内，换机需重建（命令见 §1）。
- hermes-agent 的真实执行需装其全量依赖，当前只到 collect 层；如后续要跑，另建 venv 并记录。

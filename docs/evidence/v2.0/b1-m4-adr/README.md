# B1 · M4 的 ADR（pytest 并行取 xdist 还是平台分片）

> 对应 `06-收口执行计划-20261106.md` §3 **B1**；对应 `TEST-PLAN.md` §3 双内核矩阵 **M4**。
> 执行时间：2026-11-07 14:46–15:02（CST）。环境：WSL2 Ubuntu-24.04 `~/robot-autotest`，
> 后端 `:8081`（jar 重打包后重启），MySQL `127.0.0.1:3306`；
> pytest 执行体 `~/venvs/deepseek-harness/bin/python`（pytest 9.2.0 / xdist 3.8.0 / json-report 1.5.0）。
> 全部读数来自真实运行，原始产物在 `raw/`。

## 1. 判据与结论

| 判据（06 计划原文） | 结论 | 依据 |
|---|---|---|
| ADR 落 `docs/adr/` | **PASS** | `docs/adr/0007-pytest-parallel-xdist.md`（已加入 `docs/adr/README.md` 索引） |
| 被 `PRD` / `TEST-PLAN` 引用 | **PASS** | `PRD.md` FR-v2.0-14 / FR-v2.0-21 引用 ADR-0007；`TEST-PLAN.md` §3 M4 行改为「ADR-0007 定案：xdist `-n N`」 |
| M4 按 ADR 跑出 1 份实跑证据 | **PASS** | 真实工程 254 条，串行/并行各 2 轮；并行 json-report 254 条、重复 0；nodeid 集合与串行逐条一致（丢失 0 / 多出 0）；`RESULT: PASS (不重复不丢)` |

## 2. 代码变更（让 ADR 可执行）

| 文件 | 变更 |
|---|---|
| `backend/src/main/java/com/qa/autotest/application/kernel/PytestKernel.java` | `processes > 1` 时 argv 追加 `-n <processes>`；`processes = 1` 不加（与 A2/A3 串行命令逐字等价）；类注释指向 ADR-0007 |
| `backend/src/test/java/com/qa/autotest/application/kernel/PytestKernelTest.java` | 新增 `commandUsesXdistOnlyWhenParallel`（串行不含 `-n`；`processes=4` 时 `-n 4`） |

编译与测试读数：`mvn -o -B -Dtest=PytestKernelTest test` → **Tests run: 5, Failures: 0, Errors: 0**（`raw/mvn-PytestKernelTest.log`）。

## 3. 产品 argv（真实 HTTP，非手工拼）

`POST /api/auth/login`（夹具账号 `qa-e2e-admin`）→ `GET /api/kernels/command?projectId=29&kernel=pytest&processes=…`：

```
processes=4 -> ["pytest","--json-report","--json-report-file=artifacts/kernel-pytest-1/pytest-report.json",
                "--junitxml=artifacts/kernel-pytest-1/junit.xml","-q","-n","4","fixtures/smoke/2node-smoke.robot"]
processes=1 -> [...,"-q","fixtures/smoke/2node-smoke.robot"]        # 无 -n
```

原始响应：`raw/product-argv-p4.json`、`raw/product-argv-p1.json`（均 `http=200`）。

## 4. M4 实跑（两轮串行 / 两轮 `-n 4`）

复跑：`bash docs/evidence/v2.0/b1-m4-adr/run-m4.sh 4`

| 轮次 | 命令 | pytest 末行 | json-report 条数 | 重复 | 相对串行 |
|---|---|---|---|---|---|
| 串行 r1 | 无 `-n` | `245 passed, 9 skipped in 31.01s` | 254 | 0 | 基准 |
| 并行 r1 | `-n 4` | `245 passed, 9 skipped in 26.47s` | 254 | 0 | 丢失 0 / 多出 0 |
| 串行 r2 | 无 `-n` | `245 passed, 9 skipped in 30.67s` | 254 | 0 | 丢失 0 / 多出 0 |
| 并行 r2 | `-n 4` | `245 passed, 9 skipped in 27.05s` | 254 | 0 | 丢失 0 / 多出 0 |

墙钟（`raw/m4-timing.txt`）：串行均值 **31,208 ms**，`-n 4` 均值 **27,068 ms**，
加速 **1.15×**。判定原文 `raw/m4-compare.txt`：

```
RESULT: PASS (不重复不丢)
```

**结论口径**：`pytest-json-report` 在 xdist 下由 master 聚合成**单份**报告，nodeid 集合
与串行逐条一致；这一点是本 ADR 的关键事实，已由两轮独立运行复现。
加速比只有 1.15× 是本样本（IO / 短用例为主）的属性，**不作为 xdist 的通用性能承诺**。

## 5. 缺口（不属 B1 判据，但必须记录，避免「ADR 落地即完美」的错觉）

| 编号 | 缺口 | 证据 | 影响 |
|---|---|---|---|
| G-B1-1 | `KernelController` 的命令预览把 RF 夹具路径 `fixtures/smoke/2node-smoke.robot` 硬编码给所有内核，pytest 预览拿不到真实目标路径 | `raw/product-argv-p4.json` | 预览只能证明「flag 形状」，不能直接当可执行命令；本证据的真实运行按同形状替换目标路径 |
| G-B1-2 | `GET /api/kernels` 实测 `pytest=false`：`EnvironmentProbe.onPath` 只扫服务进程 `PATH`，而 venv 在 `~/venvs/pytest-kernel/bin`，不在 unit 的 `PATH` 里 | `raw/capabilities.json` | 前置探测会把「可用的 pytest」误报为不可用（FR-v2.0-19 口径受影响）；修它要改部署 PATH 或让 probe 读 `config/pytest-venv.yml`，属部署决定 |
| G-B1-3 | pytest 预览的 `source` 文案仍写「PabotCommandBuilder / C4 白名单命令」 | `raw/product-argv-p4.json` | 仅文案不准确，不影响命令 |

## 6. 文件

| 文件 | 内容 |
|---|---|
| `run-m4.sh` | 一键复跑（2 轮串行 + 2 轮 `-n N` + nodeid 集合比对） |
| `raw/product-argv-p4.json` / `raw/product-argv-p1.json` | 产品 HTTP 预览 argv（含 / 不含 `-n`） |
| `raw/capabilities.json` | `GET /api/kernels` 能力矩阵（`pytest=false`） |
| `raw/m4-serial-r{1,2}.{log,json,xml}` | 串行两轮原始日志与产物 |
| `raw/m4-xdist-r{1,2}.{log,json,xml}` | `-n 4` 两轮原始日志与产物 |
| `raw/m4-compare.txt` | 逐条 nodeid 比对与 RESULT |
| `raw/m4-timing.txt` | 四轮墙钟读数 |
| `raw/mvn-PytestKernelTest.log` | 单测 5 条全绿 |

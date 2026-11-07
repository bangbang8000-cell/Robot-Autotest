# A3 证据 · 真实 RF / pabot 内核命令取证（bench 8 例 + legacy 17 TN-IMP）

- **RUN_ID**：`A3-RF-20261107_112631`
- **执行时间**：2026-11-07 11:26–11:27（GMT+8）
- **结论**：**通过**。
  - 主控参照命令由**生产代码** `RobotKernel.command` 现场生成（不是手抄），落 `raw/master.json`；
  - 原样执行后 `tools/kernel_cmd_diff.py` → **0 行差异**（bench 11 参数 / legacy 8 参数）；
  - 真实 `output.xml` 经 `OutputXmlNormalizer` 解析：bench **8/8 PASS**、legacy **17/17 PASS**，条数与
    `(--processes 2)` 合并后实际用例数一致；
  - `--dryrun` **连跑两次**，两次 `[ ERROR ]` 次数均为 **0**、退出码均为 **0**（双判，不单看退出码）。

## 1. 取证方法（为什么这样可信）

A3 的判据不是「单测里 assertEquals」，而是**真实进程 + 真实产物**。做法：

1. **参照命令**：用 `CmdDump build` 直接调用生产类
   `new RobotKernel(props, new OutputXmlNormalizer()).command(request, artifacts)`，
   输出 argv JSON。`javac` 的 classpath = `backend/target/classes` + `mvn dependency:build-classpath`
   导出的依赖树，**跑的是与 :8081 同一份编译产物**。
2. **执行侧留痕**：`run_master.py` 读取 master.json，按平台同样的环境（`PATH` 前置 venv/bin、
   `TZ=Asia/Shanghai`、`LANG=C.UTF-8`、`PYTHONUNBUFFERED=1`）**原样执行该 argv**，把实际 argv 落
   `agent.json`。
3. **对比**：`tools/kernel_cmd_diff.py master.json agent.json`，归一化绝对路径后逐项比对。
   > 说明：本机只有一个执行侧，master/agent 的 argv 天然同源；本证据证明的是
   > **「参照命令可直接执行、不需要任何现场改写」**。两侧**独立生成**的等价性由 NODX-03
   > （主控 / :8082 节点各自落盘）已覆盖，两者互补。
4. **解析**：`CmdDump parse` 调用生产 `OutputXmlNormalizer.fromOutputXml`，读真实 output.xml 里的
   `<test>/<status>`，输出条数与逐条结论。
5. **dryrun 双判**：同一参照命令 + `--dryrun` 连跑两轮，分别记录**退出码**与**日志中 `[ ERROR ]` 计数**。

## 2. bench：`fixtures/bench/4suite-2cases`（4 suite × 2 case，每条固定 Sleep 6s）

参照命令（`raw/master.json`）：

```json
{"runId":"A3-RF-20261107_112631","argv":["/home/evergreen/rf-stack/.venv/bin/pabot",
 "--outputdir",".../raw/artifacts/shard-1","--processes","2","--name","a3-kernel",
 "fixtures/bench/4suite-2cases/suite1.robot",".../suite2.robot",".../suite3.robot",".../suite4.robot"]}
```

| 判据 | 实测 | 来源 |
|---|---|---|
| 命令 diff | `[PASS] 命令 diff 为空（11 个参数，归一化后逐项一致）` | `raw/diff.log` |
| 真实执行 | `Total testing: 50.20 seconds` / `Elapsed time: 25.81 seconds`（2 进程并行，串行理论 48s） | `raw/stdout.log` |
| 解析条数 | `total=8 failures=0`，`Bench-1-A…Bench-4-B` 8 条全 `PASS` | `raw/parse.log` |
| 产物 | `output.xml` 8.7 KB / `log.html` 236 KB / `report.html` 242 KB / `pabot_results/` | `raw/artifacts/shard-1/` |

## 3. legacy：`fixtures/roundtrip/legacy`（17 个 `TN-IMP-*` 文件 = 17 条用例）

四 suite → 目录级单入口，`processes=1`（`PabotCommandBuilder` 强校验 `processes ≤ suitePaths.size()`，
传 2 会抛 `IllegalArgumentException`，这是**设计约束**，不是缺陷）。

| 判据 | 实测 | 来源 |
|---|---|---|
| 命令 diff | `[PASS] 命令 diff 为空（8 个参数，归一化后逐项一致）` | `raw/legacy-diff.log` |
| 真实执行 | `Elapsed time: 10.45 seconds` | `raw/legacy-stdout.log` |
| 解析条数 | `total=17 failures=0`，17 条全 `PASS`（`TN-IMP-014 网卡信息检查`…） | `raw/legacy-parse.log` |
| 产物 | `output.xml` 67 KB / `log.html` 264 KB | `raw/legacy-artifacts/shard-1/` |

## 4. `--dryrun` 双判

| 轮次 | 退出码 | `[ ERROR ]` 次数 | 末行 |
|---|---|---|---|
| 1 | 0 | **0** | `Logs flushed successfully.` |
| 2 | 0 | **0** | `Logs flushed successfully.` |

原始日志 `raw/dryrun-1/combined.log`、`raw/dryrun-2/combined.log`；计数落
`error-count.txt`。**退出码不可信**：RF/pabot 在「测试失败」和「语法错误」时都可能返回 1，
所以必须文本扫 `[ ERROR ]` 才算数；本次两次都干净。

## 5. 复跑

```bash
bash ~/robot-autotest/docs/evidence/v2.0/a3-rf-kernel/run.sh
```

前置：`backend/target/classes` 已构建（`mvn -q -DskipTests package` 或门禁跑过），
依赖 classpath 在 `/tmp/backend-cp.txt`（生成命令见 `run.sh` 注释与
`mvn -o dependency:build-classpath -Dmdep.outputFile=/tmp/backend-cp.txt`）。

## 6. 文件

| 文件 | 内容 |
|---|---|
| `run.sh` | 一键复跑（build → 执行 → diff → parse → legacy → dryrun×2） |
| `CmdDump.java` | 调生产 `RobotKernel.command` / `OutputXmlNormalizer` 的取证入口 |
| `run_master.py` | 原样执行参照命令并落 agent.json + 原始 stdout/stderr |
| `raw/master.json` / `raw/agent.json` | 参照命令 / 执行留痕 |
| `raw/diff.log` | `[PASS] … 11 个参数` |
| `raw/parse.log` | `total=8 failures=0` + 8 条明细 |
| `raw/stdout.log` / `raw/stderr.log` | pabot 原始输出 |
| `raw/artifacts/shard-1/` | 真实 `output.xml` / `log.html` / `report.html` / `pabot_results/` |
| `raw/legacy-*` | legacy 17 条的同构证据 |
| `raw/dryrun-{1,2}/` | 两次 dryrun 日志与 `[ ERROR ]` 计数 |

## 7. 遗留

- `CmdDump` 是**取证辅助**，放在 evidence 目录、编译到 `/tmp`，不进 `backend/src`；
  若后续要长期化，应做成受支持的 CLI 而非测试外挂。
- 依赖 classpath 依赖本机 `~/.m2` 缓存；换机需联网/预热依赖。
- bench 的 `Sleep 6s` 是压测固定形态（文件头已写明「不得当作功能验收依据」），
  本条证据只用于**内核命令/解析/并行**，不用于功能断言。

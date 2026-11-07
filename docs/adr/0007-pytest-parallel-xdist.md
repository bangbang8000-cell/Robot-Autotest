# ADR-0007 · pytest 并发走 xdist（`-n N`），不做平台侧分片

- **状态**：已采纳（待大师复核签字）
- **日期**：2026-11-07
- **决策人**：AI 代定（依据 2026-11-07 大师授权「需决策处按建议定，完成后交人工复核」）｜ 实现与取证：学徒（AI）
- **约束范围**：v2.0 起的 pytest 内核执行（KRN-03）、并发语义（FR-v2.0-21）、能力矩阵（FR-v2.0-14）

## 背景

`TEST-PLAN.md` §3 的双内核兼容矩阵里，**M4 = pytest × 单项目 × 并行**，原文写明
「xdist 或平台分片二选一，**需 ADR**」，判据是「结果不重复不丢」。在本 ADR 之前：

1. **平台没有任何 pytest 并行路线**。`PytestKernel.command` 只生成单进程 `pytest` 命令，
   `KernelRequest.processes` 被忽略；RF 侧则走 pabot 分片（`PabotCommandBuilder` +
   `ArtifactVerifier.checkShards`）。也就是说 M4 在平台侧不可执行、也无判据。
2. **两个候选路线的成本差一个量级**。平台分片要为 pytest 补「collect → 按 nodeid 切分 →
   N 个进程 → 合并 json/junit → 分片完整性校验」整条链路；xdist 只是给命令行加 `-n N`，
   聚合由插件在 master 完成。
3. **pytest 专用 venv 的白名单本来就包含 xdist**。`config/pytest-venv.yml` 的
   `plugins.allow` = `pytest` / `pytest-json-report` / `pytest-xdist`（A1 定案），
   A2 取证用的 `~/venvs/deepseek-harness` 里实测为 pytest 9.2.0 + xdist 3.8.0 + json-report 1.5.0。
4. **未验证的关键事实**：`pytest-json-report` 在 xdist 下能否被 master 聚合成一份完整报告
   （若 worker 各写一份，平台主通道会读到残缺 JSON）。这条必须用真实运行回答，不能靠文档推断。

## 选项

| 选项 | 优点 | 代价 | 为何不选 / 为何选 |
|---|---|---|---|
| ① **pytest-xdist**（`-n N`） | 实现成本≈0（一个参数）；聚合、重试、worker 生命周期由插件负责；与 A1 白名单一致；命令可读、可人工复现 | 并发度只在单机单进程组内切分，无法跨节点；插件缺失时 `-n` 直接报错，需前置探测；报告里不保留「哪个 worker 跑的」这层信息 | **选它**：M4 要的是「不重复不丢」，xdist master 聚合在真实 254 条样本上两轮全部 collection 一致（见下） |
| ② 平台分片 | 与 RF/pabot 路径统一，可分片到节点，可做分片完整性校验 | 需要 collect + 切分 + 多进程 + 合并 + 校验五段新代码，且 pytest 的 fixture 作用域/参数化 id 跨分片语义要另行定义 | 不选：成本高且当前无跨节点需求；将来若需要跨机，再按 ADR-0008 起平台分片（本 ADR 不封死） |
| ③ 不做并行，M4 记为「不支持」 | 零成本 | G8 第 2 条要求 M1–M10 全有实跑证据，缺 M4 等于矩阵不完整 | 不选：A1 已把 xdist 装进白名单，无理由放弃 |

## 决定

**pytest 并行统一由 pytest-xdist 承担：`processes > 1` 时命令追加 `-n <processes>`，
`processes = 1` 时不加任何参数（与 A2/A3 取证时的串行命令逐字等价）。**

边界（明确做什么、不做什么）：

- **做**：`PytestKernel.command` 按并发数追加 `-n N`；主通道仍 `--json-report`，兜底仍 `--junitxml`；
  串行与并行使用同一套归一逻辑（`NormalizedRun` / `CaseStatus`），不新增状态。
- **不做**：不在平台侧切分 nodeid、不合并多份 pytest 报告、不改 `ArtifactVerifier.checkShards`
  的语义（它只服务 pabot 的 `shard-*` 目录）。
- **不做**：不把 `processes` 透传到 `PabotCommandBuilder`（RF 内核的分片口径不变）。
- **前置条件**：执行环境必须装 `pytest-xdist`（`config/pytest-venv.yml` 白名单已含）。
  未装时 `-n` 会被 pytest 判为未知参数，属**硬失败**而非静默降级——这是有意的：
  宁可报错，也不要「说并行其实串行」。

## 实测（2026-11-07，真实 pytest 工程）

- **样本**：`~/deepseek-harness`，`--collect-only -q` 与 json-report 均为 **254 条**
  （A2 已逐条 IDENTICAL）；执行体 `~/venvs/deepseek-harness/bin/python`（pytest 9.2.0 / xdist 3.8.0）。
- **方法**：串行与 `-n 4` 各跑两轮，命令与产品 argv 同形
  （`pytest --json-report --json-report-file=… --junitxml=… -q [-n 4]`），按 nodeid 集合逐条比对。
- **读数**（原始产物 `docs/evidence/v2.0/b1-m4-adr/raw/`）：

| 轮次 | 命令 | 结果 | json-report nodeid | 重复 | 相对串行丢失/多出 |
|---|---|---|---|---|---|
| 串行 r1 | 无 `-n` | 245 passed, 9 skipped / 31.01s | 254 | 0 | —（基准） |
| 并行 r1 | `-n 4` | 245 passed, 9 skipped / 26.47s | 254 | 0 | 0 / 0 |
| 串行 r2 | 无 `-n` | 245 passed, 9 skipped / 30.67s | 254 | 0 | 0 / 0 |
| 并行 r2 | `-n 4` | 245 passed, 9 skipped / 27.05s | 254 | 0 | 0 / 0 |

- **结论**：`xdist` 下 json-report 由 master 聚合为**单份 254 条**，两轮集合与串行**完全一致**
  （`RESULT: PASS (不重复不丢)`）；墙钟 31.2s → 27.1s（**1.15×**，样本以 IO/短用例为主，
  加速比不代表 CPU 密集工程）。
- **命令侧佐证**：`GET /api/kernels/command?kernel=pytest&processes=4` 返回的 argv 已含
  `-n 4`，`processes=1` 时不含（`raw/product-argv-p4.json` / `raw/product-argv-p1.json`）。
- **单测**：`PytestKernelTest` 5 条全绿（新增 `commandUsesXdistOnlyWhenParallel`）。

## 后果

- **正面**：M4 从「无路线、无证据」变为「有 ADR、有实跑、有单测」；并发实现成本≈0；
  串行命令保持逐字等价，A2/A3 的取证口径不回退。
- **负面（必须承认）**：
  1. **加速比受用例形态限制**，本例只有 1.15×；不要把它当「并行一定快 4 倍」的承诺。
  2. **xdist 不能跨机**：跨节点仍需平台分片（当前无需求，未做）。
  3. **能力矩阵仍看不到 pytest**：`GET /api/kernels` 实测 `pytest=false`——
     `EnvironmentProbe.onPath` 只扫服务进程的 `PATH`，而 venv 在 `~/venvs/pytest-kernel/bin`，
     不在服务 unit 的 `PATH` 里（`raw/capabilities.json`）。这会让前置探测误报「不可用」，
     属**独立缺口 G-B1-2**，不在本 ADR 内修（改 PATH 属部署决定）。
  4. **命令预览的 `source` 文案仍写「PabotCommandBuilder / C4 白名单命令」**，对 pytest 不准确
     （`KernelController.java:58`），属缺口 G-B1-3。
- **触发复核的条件**：出现跨机并行需求；或某工程的 xdist 加速比 < 1.0（聚合开销反噬）；
  或 `pytest-json-report` 升级后 xdist 聚合行为变化（升级须重跑 `run-m4.sh`）。

## 关联

- WBS：KRN-01 / KRN-03 / KRN-05
- 相关文件：`backend/src/main/java/com/qa/autotest/application/kernel/PytestKernel.java`、
  `config/pytest-venv.yml`、`scripts/setup-pytest-venv.sh`
- 证据：`docs/evidence/v2.0/b1-m4-adr/README.md`（含 `run-m4.sh`、`raw/`）
- 上游依据：`docs/release/v2.0-platform/TEST-PLAN.md` §3 M4；
  `docs/release/v2.0-platform/PRD.md` FR-v2.0-14 / FR-v2.0-21；
  `docs/release/v2.0-platform/06-收口执行计划-20261106.md` §3 B1
- 相关 ADR：ADR-0005（双模用例模型，pytest 为脚本模之一）

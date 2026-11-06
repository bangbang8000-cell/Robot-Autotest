# W3 / KRN-01..05 交付证据（2026-11-06）

| 包 | 实现 | 本机证据 |
|---|---|---|
| KRN-01 | `domain/kernel/` SPI + `ExecutionKernel` + `NormalizedRun` + `KernelRegistry` + ArchUnit 边界 | `NormalizedRunTest` 4 条、`KernelBoundaryTest` 1 条（精确包前缀，不用宽匹配） |
| KRN-02 | `RobotKernel` 直接委托 `PabotCommandBuilder`；`OutputXmlNormalizer` 复用 RF 判定 | `RobotKernelParityTest`：命令 `argv` 与旧 builder **逐字相等**（`assertEquals(legacy, kernel.command(...))`） |
| KRN-03 | `PytestKernel` json-report 主通道 / junitxml 兜底 / 参数化展开 / 六态映射 | `PytestKernelTest` 4 条（7 条六态样本、4 条兜底、缺产物拒绝、双通道命令） |
| KRN-04 | `KernelSelector` 项目默认 → 用例集覆盖 → 用例覆盖，返回生效来源与覆盖标记 | `KernelSelectorTest` 4 条（含未知内核拒绝、注册名重复拒绝） |
| KRN-05 | `EnvironmentProbe` 能力矩阵 / PATH 探测 / 进程树终止 / 统一超时重试 | `GET /api/kernels` 返回 capabilities；`sameRunMixedKernels=false` 落实 D8 |

## 双内核兼容矩阵 M1–M10（分层写实）

| 组合 | 本机可验部分 | 状态 | 真机 / 外部样本 |
|---|---|---|---|
| M1 RF × 单项目 × 串行 | 命令等价 + output.xml 归一单测 | `PARTIAL` | 待现场 |
| M2 RF × 单项目 × 并行 4 | `PabotCommandBuilder` 分片参数透传 | `PARTIAL` | 待现场（20 轮压测） |
| M3 pytest × 单项目 × 串行 | 六态映射 + 参数化展开单测 | `PARTIAL` | 待现场 |
| M4–M10 其余组合 | 归一逻辑与选择粒度单测 | `PARTIAL` | 待现场 |

**没有把 M1–M10 记 PASS**：G4 的 20 轮压测是空模板、真实 pytest / RF 工程尚未在本机完整执行链跑通。
按 `TEST-PLAN.md` §6「本机 PARTIAL / 真机 NOT-RUN」分层口径，相关 TC 不冒充通过，**不申请 G8**。

## 真实样本预检（C2 / C3）

| 样本 | 结构预检 | 完整执行链 |
|---|---|---|
| C2-a `~/.hermes/hermes-agent/tests/`（5178 个 test_*.py） | 路径与规模已登记 | `NOT-RUN（待现场）`：未安装其依赖，不在本机跑 collect |
| C2-b `~/deepseek-harness/`（22 个 test_*.py + pytest.ini） | 同左 | 同上 |
| C3-a `fixtures/roundtrip/legacy/*.robot`（17 条 TN-IMP） | 命令等价单测使用 fixture 路径 | `NOT-RUN（待现场）` |
| C3-b `fixtures/bench/4suite-2cases/` | 同上 | 同上 |

**禁止玩具工程顶替**：本波没有任何合成工程被用来声称「真实样本导入执行通过」。

## 边界

- `GET /api/kernels/command` 用固定 fixture 预览命令，不接受任意路径参数（避免任意路径读取）。
- 同一 run 不混跑双内核（D8）：能力矩阵里 `sameRunMixedKernels=false`，由选择粒度单点决定。
- 历史 run 不批量补报告（D9）：本波不触碰历史 `run` 数据。
- **G4 未判过、G8 不申请**；发版与对外口径仍归大师。

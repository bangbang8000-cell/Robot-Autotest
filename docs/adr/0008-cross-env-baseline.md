# ADR-0008 · 跨环境手动基线的产品口径与差异标注

- **状态**：已定案（待大师复核签字）｜**实现未落地**，见文末「实现项」
- **日期**：2026-11-07
- **决策人**：AI 代定（依据 2026-11-07 大师授权）｜ 取证：学徒（AI）
- **约束范围**：报告对比（RPT-03 / FR-v0.5-12~16）、趋势（RPT-04 / FR-v0.5-20）、对外交付口径

## 背景

- **自动基线**（`CompareService.resolveBaseline` 的自动分支）：同项目 + 同 `DUT`/`LINK` + 早于本轮 +
  `state=finished` 中最近一次；找不到就返回 `null`，不报错。这个口径与 FR-v0.5-20
  「趋势只统计同环境同用例」一致。
- **手动基线**（`baselineRunId`）：当前实现要求**同项目 + 同 `DUT`/`LINK`**，否则 **400**
  （`CompareService.java:166-183`，中文原因「手动基线与本轮环境不一致：本轮 DUT=… LINK=…」）。
  问题在于：**FR-v0.5-14 只约束了自动基线的「同环境」，没有规定手动基线**；
  因此「400 拒绝」是实现自行加的口径，不是产品明文。平台里「环境」的可比单位是
  `DUT`/`LINK` 二元组（不是 `environment.id`），下文统一按此口径表述。
- 现实需求：换机 / 升级前后（如 147→149、OFED 升级）需要把同一批用例的两轮结果并排看。
  这类对比**必须能看**，但**不能默认混算**——跨环境差异可能来自 OS/固件/硬件，而不是被测代码。

## 决定

1. **自动基线保持同 DUT/LINK**（现状不变）：自动绝不跨环境，避免默认口径漂移。
2. **手动基线允许跨 DUT/LINK**，但必须满足三条硬约束：
   - **显式选择**：只能由用户从候选里点选（`baselineRunId`），不提供「自动跨环境」；
   - **必须标注**：对比结果顶层携带 `crossEnvironment=true`，并分别给出两侧
     `DUT` / `LINK`（以及可得的 OS / 内核 / 软件版本指纹）；
   - **必须警示**：报告页与导出件（PDF/Word/MD）在对比区顶部显示
     「**跨环境对比，仅用于排障参考，不得作为验收基线**」。
3. **差异标注字段**（契约固定，避免前端各自解释）：

| 字段 | 位置 | 取值 | 含义 |
|---|---|---|---|
| `crossEnvironment` | `CompareResult` 顶层 | `true/false` | 本轮与基线是否不同环境 |
| `currentEnvironment` / `baselineEnvironment` | `CompareResult` 顶层 | `{id,name}` | 两侧环境，跨环境时必填 |
| `envDiff` | 每条 `CompareRow` | `none / cross` | 该行是否跨环境（当前恒等于顶层，留给将来逐用例覆盖） |
| `summary.newFail/newPass/bothFail/notRun` | `CompareResult.summary` | 整数 | 沿用现状；**跨环境时不得只报计数**，必须同时带 `crossEnvironment=true` |

4. **仍然 400 的只剩两种情况**：基线 run 不存在；基线不属于当前项目（租户隔离）。
   「不同环境」不再作为 400 理由。

## 不做什么

- 不做「跨环境自动选基线」；不做跨环境的趋势连线（FR-v0.5-20 不变）；
- 不把跨环境结论写进任何「验收通过」判定或门禁；不做环境差异的自动归因（那需要另立需求）。

## 后果

- **正面**：换机/升级场景有可用的并排视图，且不会被误当验收依据；自动基线口径不受影响。
- **负面**：需要改 `CompareService` 的 400 分支、`CompareResult` 契约、前端横幅与导出模板；
  导出件新增一段文案，PDF 版面要复验（与 A8 的 CJK 缺口 G-A8-1 一起处理）。
- **过渡期口径**：实现落地前，代码仍 400。**对外文档与答辩一律按「当前不支持跨环境手动基线」表述**，
  不得引用本 ADR 宣称已支持。

## 实现项（未做，排期另定）

| 编号 | 内容 | 落点 |
|---|---|---|
| W-B6-1 | `CompareService` 去掉「同环境」400，改为 `crossEnvironment` 计算 | `application/report/CompareService.java` |
| W-B6-2 | `CompareResult` / `CompareRow` 加 `crossEnvironment` / `envDiff` / 两侧 environment | 同上 + `api/report/ReportController` |
| W-B6-3 | 前端对比页顶部警示条 + 单测 | `ReportDetailView.vue` + `*.spec.ts` |
| W-B6-4 | 导出件（PDF/Word/MD）加警示与两侧环境 | `ExportService` 模板 |

## 关联

- 上游依据：`docs/release/v0.5-report/PRD.md` FR-v0.5-12~16 / FR-v0.5-20（本次已回写口径）；
  `docs/release/v2.0-platform/06-收口执行计划-20261106.md` §4 **B6**
- 代码：`backend/src/main/java/com/qa/autotest/application/report/CompareService.java#L166-L183`
- 相关 ADR：ADR-0007（同一批「先定案再实现」的决策）

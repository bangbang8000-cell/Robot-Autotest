# W2 / RPTX-01..05 交付证据（2026-11-06）

| 包 | 实现 | 本机证据 |
|---|---|---|
| RPTX-01 | `test_report` + `ReportState` 状态机 + `TestReportService` + `TestReportController` | `ReportStateMachineTest` 4 条；迁移 `V8_1__test_report.sql` + down |
| RPTX-02 | `ReportSnapshotService` 物化 JSON/HTML + SHA-256 + 版本号；渲染路径只读 `test_report` | `ReportSnapshotTest` 4 条（hash 稳定 / 自包含 / 脱敏 / 对内视图） |
| RPTX-03 | `ReportViewRenderer.external()` 走 C5 白名单 | `ReportSnapshotTest.externalViewOnlyHasWhitelistedFields` |
| RPTX-04 | `ReportViewRenderer.internal()` 保留关键字树 / 命令 / 原始输出入口 | `ReportSnapshotTest.internalViewCarriesExecutionDetails` |
| RPTX-05 | `ReportExportService` HTML / PDF / DOCX + 水印 + 保留策略 | `ReportExportTest` 4 条；三格式样件 + `sensitive-scan.log` |

**新增表（C9 登记）**：`test_report`、`test_report_export`、`test_report_audit`。
实测 `scripts/rollback.sh` 走 `mysqldump` 全库，新表自动纳入备份，无需逐表登记清单
——此结论已回写 `docs/plan/前置条件闭环-20261106.md` 的 C9 口径。

**边界**：三份导出样件是**本机内部验证件**，不是真实链路导出；G8 第 5 条「冻结后删源 run
仍可渲染」的本机保证是「渲染只读快照表」，真机删数演练与导出件抽样仍记 `待现场`。
EXTERNAL 报告**未对外发送**，C5 定案不等于获准对外。

**页面入口**：`/test-reports`（8001 统一入口直达）；报告页底部「测试报告」按钮；
`navigation.ts` 的 `reports` 叶子把 `/test-reports` 加入 `alsoMatch`，侧栏仍高亮「报告」域。
一级菜单结构未改（六域 / 层级 ≤ 2 不变）。

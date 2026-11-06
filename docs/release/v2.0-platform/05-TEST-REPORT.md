# v2.0 平台化 · ⑥ 测试报告（W1–W7 分层实测台账）

**文档编号** RA-REL-2026-020-TR ｜ **版本** v2.0-platform ｜ **日期** 2026-11-06
**状态** W1–W7 34 包已开发并本机分层实测；**G4 / G5 / G8 均未判过、不申请**
**说明** 本文件在 2026-11-06 W1 收口时由「计划态快照」改写，W2–W7 收口时按实测逐行回写。结果分三档：
`PASS` = 本轮有直接机器证据；`PARTIAL` = 单测/纯逻辑通过但真实页面、实库写入、WS 握手或键盘 E2E 未跑；
`NOT-RUN` = 前置未满足或演练未做。**PARTIAL 不得读作通过；W1–W7 本机结果不得读作 v2.0 通过，更不得读作 G4/G5/G8 已判过。**

---

## 1. 结论

| 项 | 值 |
|---|---|
| 开工日期 | 2026-11-06 |
| 本版 TC | 67 条（TC-v2.0-01~67），W1–W7 全部有结果行 |
| PASS | **15**（有直接本机机器证据） |
| PARTIAL | **42**（单测/纯逻辑/迁移通过，真实页面、实库写入、WS、真机未跑） |
| 失败 | 0 |
| NOT-RUN | **10**（真机 / 真实浏览器 / 外部样本 / 演练条件未具备） |
| 本轮机器门 | 八步门禁 `FAILS=0`（10/10）；后端 773 条 / 前端 239 条单测；入口实测 `12/12`；版本机检 `FAIL 0` |
| 当前结论 | **W1–W7 功能开发完成、八步机器门 FAILS=0；v2.0 未通过 G8（G4/G5 未判过、不申请）** |

---

## 2. 执行台账

| TC | 覆盖 FR | 结果 | 证据位置 | 备注 |
|---|---|---|---|---|
| TC-v2.0-01 | FR-v2.0-01 | PARTIAL | `TestReportsView.vue` + `ReportViewRenderer.internal()/external()` | 双视图与路由已落；页面 E2E 未跑 |
| TC-v2.0-02 | FR-v2.0-02 | PARTIAL | `ReportViewRenderer.internal()` | FOR/IF 关键字树、命令与原始输出入口保留；真实 run 未跑 |
| TC-v2.0-03 | FR-v2.0-03 | PARTIAL | `ReportSnapshotTest` + `V8_1__test_report.sql` | 快照自包含 + SHA-256 单测通过；删源 run 演练待现场 |
| TC-v2.0-04 | FR-v2.0-04 | PARTIAL | `NormalizedRun.counts()` + `ReportSnapshotService` | 分母不含跳过已实现；3-run 聚合未实跑 |
| TC-v2.0-05 | FR-v2.0-05 | PARTIAL | `docs/evidence/v1.0/w2-report/export-sample.*` + `sensitive-scan.log` | 本机三格式内部样件、敏感扫描 0 命中；真实链路导出抽样待现场 |
| TC-v2.0-06 | FR-v2.0-06 | PARTIAL | `TestReportController` 项目访问校验 + `ForbiddenView.vue` | 服务端校验与 403 页已落；真实账号 403 全链路未跑 |
| TC-v2.0-07 | FR-v2.0-06 | PASS | `ReportStateMachineTest` 4 条 | DRAFT→FROZEN→REVOKED/ARCHIVED，非法跃迁被拒 |
| TC-v2.0-08 | FR-v2.0-06 | PARTIAL | `TestReportService` 保留策略 | 策略已实现；过期清理与审计未实跑 |
| TC-v2.0-09 | FR-v2.0-04 | PARTIAL | `EXTERNAL_FIELDS.trend` 白名单 | 趋势字段在白名单；两次 run 对比未实跑 |
| TC-v2.0-10 | FR-v2.0-07 | PASS | `config/navigation.spec.ts` | 6 域 / 层级 ≤2 / 能力过滤，单测断言 |
| TC-v2.0-11 | FR-v2.0-08 | PARTIAL | `navigation.spec.ts` + `ForbiddenView` + 路由守卫 | 4 角色覆盖；直达无权限路由 E2E 未跑 |
| TC-v2.0-12 | FR-v2.0-09 | PARTIAL | `config/commandPalette.spec.ts` | 搜索/最近 5/收藏逻辑通过；键盘 E2E 未跑 |
| TC-v2.0-13 | FR-v2.0-10 | PARTIAL | `SystemSettingServiceTest` | 默认值/403/非法值/审计通过；实库写入未跑 |
| TC-v2.0-14 | FR-v2.0-10 | PARTIAL | `SystemSettingServiceTest` | 项目/平台隔离通过；页面回显未跑 |
| TC-v2.0-15 | FR-v2.0-11 | PARTIAL | `BuildInfoServiceTest` + `/api/system/build-info` | 端点存在（verify-entry）；页面实渲未跑 |
| TC-v2.0-16 | FR-v2.0-12 | PASS | `config/changelog.spec.ts` + verify-entry `/CHANGELOG.md` 200 | 四类齐全/当前唯一/倒序，静态数据可达 |
| TC-v2.0-17 | FR-v2.0-13 | PARTIAL | `config/commandPalette.spec.ts` | 折叠/收藏持久化通过；切项目 UI 未跑 |
| TC-v2.0-18 | FR-v2.0-14 | PASS | `KernelBoundaryTest` | ArchUnit：domain.kernel 不依赖 api/application/infrastructure/Spring/Jackson |
| TC-v2.0-19 | FR-v2.0-15 | PASS | `CaseStatus.fromPytest()` + `NormalizedRunTest` | 六态映射，XPASS 记失败 |
| TC-v2.0-20 | FR-v2.0-15 | NOT-RUN | `docs/evidence/v1.0/w3-kernel/README.md` | 真实 pytest 工程实跑需 C2 现场 / G4（未判过） |
| TC-v2.0-21 | FR-v2.0-16 | PARTIAL | `RobotKernel` + `PabotCommandBuilderTest` | 命令逐字等价有单测；真机 RF 实跑未做 |
| TC-v2.0-22 | FR-v2.0-17 | PARTIAL | `PytestKernel` 参数化 `[param]` 保留 | 实现已落；3 组参数实跑未做 |
| TC-v2.0-23 | FR-v2.0-18 | NOT-RUN | `docs/evidence/v1.0/w3-kernel/README.md` | 1 万条写入 + <=30s 压测需真库 / G4 |
| TC-v2.0-24 | FR-v2.0-19 | PARTIAL | `EnvironmentProbe.capabilities()` | 能力矩阵已落；无 pytest 环境 422 未端到端验 |
| TC-v2.0-25 | FR-v2.0-20 | PARTIAL | `KernelSelector` | 项目/用例集/用例三级 + source 标记有单测；页面覆盖标记未跑 |
| TC-v2.0-26 | FR-v2.0-21 | NOT-RUN | `docs/evidence/v1.0/w3-kernel/README.md` | 双内核取消进程树需真机（ADR-0013） |
| TC-v2.0-27 | FR-v2.0-21 | NOT-RUN | `docs/evidence/v1.0/w3-kernel/README.md` | 双内核超时真机未跑 |
| TC-v2.0-28 | FR-v2.0-14 | PARTIAL | `EnvironmentProbe` + `sameRunMixedKernels=false` | 能力矩阵已落；强行提交 400 未端到端 |
| TC-v2.0-29 | FR-v2.0-18 | PARTIAL | `KernelSelector` 项目默认 | 选择来源有单测；历史 run kernel 不变未实库验 |
| TC-v2.0-30 | FR-v2.0-23 | PASS | `V8_2__import_sandbox.sql` + `migration-v8_2-v8_4.log` | 迁移实跑，`case_source` 不带设备外键 |
| TC-v2.0-31 | FR-v2.0-24 | PARTIAL | `case_source` 平台/项目可见性字段 | 字段落表；两项目引用实库未验 |
| TC-v2.0-32 | FR-v2.0-25 | NOT-RUN | `docs/evidence/v1.0/w4-import/README.md` | 真实 pytest 目录 `--collect-only` 需 C2 现场 |
| TC-v2.0-33 | FR-v2.0-26 | NOT-RUN | `docs/evidence/v1.0/w4-import/README.md` | 真实 RF 目录/zip `--dryrun` 需 C3 现场 |
| TC-v2.0-34 | FR-v2.0-28 | PASS | `ImportSandboxTest` 12 条负向 + zip slip | 路径逃逸/绝对路径/盘符/NUL/过深路径全拒；zip slip 整批拒绝 |
| TC-v2.0-35 | FR-v2.0-28 | PASS | `ImportSandbox` 限额常量 + 负向单测 | 1000 项 / 64 MiB / 8 MiB / 12 层，超限不落盘 |
| TC-v2.0-36 | FR-v2.0-27 | PARTIAL | `PreflightService` | 预检报告 / 敏感扫描 / diff 实现；三态样本未逐条实跑 |
| TC-v2.0-37 | FR-v2.0-30 | NOT-RUN | `docs/evidence/v1.0/w4-import/README.md` | 导入后真实执行需 G4 / 真机 |
| TC-v2.0-38 | FR-v2.0-31 | PARTIAL | `PreflightService.diff` | 重导入 diff 实现；源更新实跑未做 |
| TC-v2.0-39 | FR-v2.0-24 | NOT-RUN | `docs/evidence/v1.0/w4-import/README.md` | 项目移除资产引用实库未验 |
| TC-v2.0-40 | FR-v2.0-29 | PASS | `ImportSandboxTest` | canonical 路径校验、只读落盘、SHA-256 入库单测 |
| TC-v2.0-41 | FR-v2.0-32 | PARTIAL | `GroupService` + `V8_3__group_auth.sql` | CRUD 实现 + 迁移实跑；真账号 HTTP 未跑 |
| TC-v2.0-42 | FR-v2.0-33 | PARTIAL | `project_group_role` + `EffectiveRoleResolver` | 授权模型落表；3 项目 403 未端到端 |
| TC-v2.0-43 | FR-v2.0-34 | PASS | `EffectiveRoleTest` 82 条 | 直接角色 + 组角色取最高，越权矩阵全绿 |
| TC-v2.0-44 | FR-v2.0-34 | PARTIAL | `EffectiveRoleResolver.invalidate(projectId)` | 缓存失效实现；下一次请求 403 未端到端 |
| TC-v2.0-45 | FR-v2.0-35 | PARTIAL | `SuperAdminViewService` | 非管理员 403；全局视图统计未实库验 |
| TC-v2.0-46 | FR-v2.0-35 | PARTIAL | `SuperAdminViewService` 单测 | 403 已验；跨项目 URL 未端到端 |
| TC-v2.0-47 | FR-v2.0-36 | PARTIAL | `ProjectPicker` 上移顶栏（W1） | 切换后列表/报告 E2E 未跑 |
| TC-v2.0-48 | FR-v2.0-37 | PARTIAL | `project_template` 落表 | 模板/归档表已落；归档后建 run 拒绝未实跑 |
| TC-v2.0-49 | FR-v2.0-38 | PARTIAL | `ScenarioServiceTest` | 一层 + `parent_scenario_id = NULL` 守门；按序执行未实跑 |
| TC-v2.0-50 | FR-v2.0-39 | PARTIAL | `AiPanelTest.everyActionWritesAudit` + `GroupService` | 全动作写审计单测；四条前后值未端到端 |
| TC-v2.0-51 | FR-v2.0-40 | PARTIAL | `layout.spec.ts` + `useAiDrawer.ts` | 三栏不重叠 / 宽度记忆有单测；浏览器开合刷新未跑 |
| TC-v2.0-52 | FR-v2.0-41 | PARTIAL | `cli-probe.log` | 2 found / 2 not found 已探测；0/1/2 组合未逐一造环境 |
| TC-v2.0-53 | FR-v2.0-42 | PASS | `AiPanelTest.contextIsScrubbedAndTruncated` | 脱敏 + 8000 字符截断 + `[TRUNCATED]` 标记 |
| TC-v2.0-54 | FR-v2.0-43 | PARTIAL | `AiPanelTest.noCliDegradesWithoutBlocking` | L1 降级不阻断已验；真实模型输出未跑 |
| TC-v2.0-55 | FR-v2.0-44 | PASS | `AiPanelTest.executeRequiresSecondConfirmation` | 缺二次确认抛 FORBIDDEN，确认后 L3 |
| TC-v2.0-56 | FR-v2.0-45 | PARTIAL | `AiPanelService.evaluate()` | 动作 + 审计实现；真实失败 run 聚类未跑 |
| TC-v2.0-57 | FR-v2.0-46 | PARTIAL | `AiProcessManager` | 非交互命令 + 进程树终止实现；真实 cwd/环境变量未检查 |
| TC-v2.0-58 | FR-v2.0-46 | PASS | `AiPanelTest.noCliDegradesWithoutBlocking` + `terminateWorksEvenWithoutCli` | 删除 CLI 后面板可用、终止与 AI 可用性无关 |
| TC-v2.0-59 | FR-v2.0-47 | PARTIAL | `check_colors.py` / `check_w7_tokens.py` | 硬编码 0 处 / 8 档栅格 / 浅深覆盖全绿；8 页 x 2 主题真实渲染待现场 |
| TC-v2.0-60 | FR-v2.0-48 | PARTIAL | `layout.spec.ts` | 本机纯规则：1440 利用率 86.1%、三列精确平铺；表格首屏 >=12 行属浏览器项未跑 |
| TC-v2.0-61 | FR-v2.0-49 | PASS | `viewState.spec.ts` + `check_states.py` | 四态全可达且仅 READY 渲染内容；焦点三态静态守门 |
| TC-v2.0-62 | FR-v2.0-50 | PARTIAL | `layout.spec.ts` + `main.css` 999px/760px 断点 | 1280 无横滚 / 自动折叠为规则级验证；浏览器走查待现场 |
| TC-v2.0-63 | FR-v2.0-51 | PARTIAL | `*:focus-visible` + `prefers-reduced-motion` | 静态样式守门通过；键盘 Tab/Esc 焦点陷阱 E2E 未跑 |
| TC-v2.0-64 | FR-v2.0-52 | NOT-RUN | `docs/evidence/v1.0/w7-design/screenshot-baseline.md` | 16 张截图基线 NOT-RUN，明确不伪造 |
| TC-v2.0-65 | FR-v2.0-53 | PASS | `scripts/verify-entry.sh` 12/12 | 首页/深链接/404/缓存头均实测 |
| TC-v2.0-66 | FR-v2.0-54 | PARTIAL | `scripts/verify-entry.sh` 双探针 | `/healthz`、`/api/health` PASS；WS 101 未实测 |
| TC-v2.0-67 | FR-v2.0-55 | NOT-RUN | `scripts/deploy.sh` 已提供 | 部署→回滚→再检查演练未跑 |

---

## 3. 说明

- 本文件在每次 Wave 完成后更新对应行（结果 / 证据位置 / 备注），**禁止事后补写「通过」**。
- 「证据位置」填 `docs/evidence/v2.0/<包>/README.md` 的锚点或具体命令输出文件。
- 若某条 TC 因前置条件缺失无法执行，结果写 NOT-RUN，备注写缺失条件与补测计划，不写「跳过」。
- **W1 证据的边界**：本轮没有跑浏览器 E2E、没有带登录态打实库写入、没有做 WebSocket 101 握手、
  没有做部署回滚演练。相应 TC 一律 `PARTIAL` / `NOT-RUN`，不因单测通过而升格。
- **G4 / G5 / G8 均未判过**：按大师 16:50 二次授权「开发并行、判定不前置」，W2–W7 功能已开发实测，
  但门禁判定一步未做、一步未代判、G8 一次未申请。见 `docs/plan/门禁核验纪要-G2-G5-20261106.md` 附 2。
- **C2/C3 真实工程**只登记为本机预检级样本；G8 第 4 条现场确认仍待，TC 记 `NOT-RUN` / `PARTIAL`。
- **UIX-04 截图基线 NOT-RUN**：不伪造 16 张截图，`UIX-04` 在 WBS 记 `待现场`。
- **历史 15 包复核**：见 `docs/plan/历史包复核结论-20261106.md`；真机 / 真账号 / 真实浏览器 /
  ADR-0013 禁跑令类缺口一律不自评通过。

## 4. 本轮机器门实测（2026-11-06）

| 门 | 命令 | 结果 |
|---|---|---|
| 八步门禁 | `bash docs/evidence/v1.0/policy-20261106/gates.sh` | 10 步全 PASS，`GATES_RESULT FAILS=0` |
| 统一入口实测 | `bash scripts/verify-entry.sh` | 12 / 12 PASS |
| 版本流程机检 | `python3 tools/check_release_docs.py` | FAIL 0 项（v2.0 PASS） |
| 后端单测 | `mvn -B test` | **773 条 / 0 failures / 0 errors**（含 W2–W7 新增、RPT-07 双格式门禁） |
| 前端单测 | `npm run test` | **239 条 / 37 个文件全绿**（含 layout / viewState / aiPanel / kernels / groups / imports） |
| 数据库迁移 | `bash scripts/db-migrate.sh` | V8_1 / V8_2 / V8_3 / V8_4 全部实跑，9 张新增表核对齐全，业务表 61 张 |
| 全量静态守门 | `bash scripts/lint.sh` | PASS（含 Checkstyle / 色值 / token / i18n / 四态 / 图标 / 表格 / 品牌 / API 前缀 / W7 token） |

> 门禁原始日志落在 `docs/evidence/v1.0/policy-20261106/logs/`（被 `.gitignore` 忽略，可由 gates.sh 复跑重建）。
> 逐项修复与证据见 `docs/evidence/v1.0/w1-delivery/README.md`。

---

## 修订记录

| 日期 | 版本 | 改了什么 | 为什么改 |
|---|---|---|---|
| 2026-11-06 | v1.0 | 初版：67 条 TC 计划态台账，全部 NOT-RUN | 版本流程要求六份产物齐全，本版尚未开工 |
| 2026-11-06 | v1.1 | **推翻 v1.0 的「未开工 / 全部 NOT-RUN」**：W1 六包已实现并本机实测，11 条 W1 TC 拆为 PASS 3 / PARTIAL 7 / NOT-RUN 1；新增 §4 机器门实测；标题与状态行同步 | 七道流程第 ⑦ 道文档回写；按「被实测推翻的假设逐条改掉并留修订记录」办理，不悄悄改数字 |

| 2026-11-06 | v1.2 | **推翻 v1.1 的「W2–W7 未开工 / 56 条 NOT-RUN」**：W2–W7 已开发并本机分层实测，56 条 TC 逐行按真实证据回写；新增双格式导出门禁、四态、三栏、双 /api 前缀订正等实测读数；§3 边界与 §4 机器门同步回写 | 七道流程第 ⑦ 道文档回写；按「被实测推翻的假设逐条改掉并留修订记录」办理，**G8 十条判定本体一项未动** |

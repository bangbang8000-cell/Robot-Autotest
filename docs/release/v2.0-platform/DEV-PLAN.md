# v2.0 平台化 · ④ 开发计划

**文档编号** RA-REL-2026-020-D ｜ **版本** v2.0-platform ｜ **日期** 2026-11-06 ｜ **状态** 已评审通过（AI 书面结论 2026-11-06，有条件）
**输入** `PRD.md`（55 条 FR）｜ **输出物** 代码、迁移脚本、测试、`docs/evidence/v2.0/<包>/`
**硬约束** ① 不改 `WBS-STATUS.md` 的 96 包台账（v2.0 新包评审通过后再并入）；② 八步门禁每批必跑；③ Flyway 只前进，down 脚本同步写。

---

## 1. 总体架构改造

### 1.1 分层与新增模块

```
api/            ReportController(新) SceneController(新) GroupController(新) AiController(新) KernelController(新)
application/    report/  kernel/  importx/  iamx/  ai/  scene/
domain/         report/Report, ReportVersion, ReportStatus
                kernel/ExecutionKernel, NormalizedRun, NormalizedCase, Capability
                importx/CaseSource, SourceItem, ImportPrecheck
                iamx/UserGroup, GroupMember, ProjectGroup, EffectiveRoleResolver
                ai/AgentProfile, AgentSession, AiAction
infrastructure/ report/Jdbc..., kernel/robot/RobotKernel, kernel/pytest/PytestKernel,
                importx/SandboxExtractor, ai/ProcessAgentRunner
```

### 1.2 内核 SPI（本版最关键的抽象）

```java
public interface ExecutionKernel {
    KernelId id();                                  // ROBOT | PYTEST
    Capability capability();                        // 参数化/fixture/标签/分片/dryrun
    ProbeResult probe(ExecutionEnvironment env);    // 可执行文件、版本、插件
    List<CollectedCase> collect(CaseSourceRef ref); // 用例树 + external_id
    CommandSpec buildCommand(RunContext ctx);       // 只出命令，不执行
    NormalizedRun parse(ArtifactSet artifacts);     // output.xml / json-report / junitxml
    FailureCategory classify(int exitCode, String tailLog, NormalizedCase c);
}
```

**边界纪律（写进 ArchUnit 规则）**
- `kernel/**` 不得依赖 `application/execution`、不得直接申请设备租约、不得写 `run` 表。
- `application/execution` 不得出现 `pabot` / `pytest` 字样（只能通过 SPI）。
- `parse()` 必须纯函数：输入产物，输出模型，不做 IO 副作用（读文件除外，只读）。

### 1.3 执行链路（改造后）

```
RunController → RunService.create（记录 kernel + kernel_source）
  → KernelResolver（用例 > 用例集/场景 > 项目 > 系统）
  → EnvironmentProbe（不可用则 422 + 指引）
  → ProjectRunDispatcher（并发/租约/超时，不变）
  → CommandSpec → ProcessLauncher（进程树管理）
  → ArtifactSet → Kernel.parse → NormalizedRun → test_result 批量入库
  → 执行报告（派生） / 测试报告（手动或按策略生成快照）
```

### 1.4 报告双实体

```
run ──1:N── test_report_run ──N:1── test_report ──1:N── test_report_version
                                        └── status: DRAFT → FROZEN → REVOKED/ARCHIVED
```

- **执行报告**：不落库，`ReportRepositoryJdbc` 继续按 run 派生；新增聚合查询（失败分类、关键字耗时）。
- **测试报告**：生成时把「渲染所需全部数据」写 `test_report_version.snapshot_json`，同时渲染 `snapshot_html`；冻结后读取只走快照。
- 快照 hash = `sha256(snapshot_json 规范序列化)`，用于防篡改与去重。

---

## 2. 数据模型变更

> 全部为 `db/migration/V8_*`，配套 `db/down/V8_*.down.sql`。现有最新为 V7_6，本版新增 6 个迁移、14 张表、5 处列扩张。

| 迁移 | 表 / 列 | 要点 |
|---|---|---|
| `V8_1__kernel.sql` | `run` +`kernel`+`kernel_source`+`degraded`+`env_fingerprint`；`test_result` +`kernel`+`external_id`+`failure_category`+`parameters_json`；`test_case` +`kernel`；`test_set` +`kernel` | 历史回填 `kernel='robot'`、`failure_category='unknown'`；索引 `(run_id, kernel)`、`(project_id, external_id)` |
| `V8_2__case_source.sql` | `case_source` / `case_source_item` / `case_source_version` | 来源登记：类型、可见性、许可证、校验和、存储路径、版本 |
| `V8_3__test_report.sql` | `test_report` / `test_report_version` / `test_report_run` | 报告状态机、版本、快照路径与 hash、run 聚合摘要 |
| `V8_4__user_group.sql` | `user_group` / `group_member` / `project_group` | `project_group.group_role` 映射项目角色；唯一键 `(project_id, group_id)` |
| `V8_5__scene.sql` | `test_scene` / `test_scene_item` | 场景为有序步骤，`item_type=CASE/SET`，`seq` 唯一 |
| `V8_6__ai_agent.sql` | `ai_agent_profile` / `ai_agent_session` / `ai_action_log` | 配置档、会话摘要、动作审计（不存思维链） |
| `V8_7__settings_seed.sql` | `system_setting` 种子 | 入口端口 8001、默认内核、AI 开关、报告保留天数 |

**兼容策略**
- 所有新列可空或带默认值；旧代码读不到新列不报错。
- `test_result.kernel` 先回填再置 `NOT NULL`（分两个迁移语句，同一文件内顺序执行）。
- `TenantGuard` 白名单同步登记 14 张新表，并补元测试（漏登记即 FAIL）。
- 备份脚本表清单同步更新，恢复演练纳入 W1 的完成定义。

---

## 3. 分批计划（Wave）

> 与规划 v3.0 §6.2 的 B9 波次表**同源**：波次编号、包清单、交付判据一致；差异只允许「本文件更细」。
> 波次间不强绑日历，按进入条件推进；每波完成即按五态把包登记为 `待验`，机器门复跑 + 证据核对通过后
> 由 AI 自行置 `已完成` 并留 `AI 验收 <日期>` 戳（2026-11-06 口径，与 `docs/plan/WBS-STATUS.md` 一致）；人做旁路建议。

| 波次 | 内容 | 包 | 依赖 | 交付判据 |
|---|---|---|---|---|
| **W1** | 统一入口 8001 + 菜单 IA + 设置/关于/版本页 + 命令面板 | ENVX-01,02；NAV-01..04 | 无（不依赖既有批次） | `:8001` 打开首页；6 域菜单按权限过滤；设置可改可审计；关于页信息真实 |
| **W2** | 报告双实体 + 快照冻结 + 双渲染 + 导出 | RPTX-01..05 | W1（页面骨架）+ **G5 已通过**（报告产物结构定型） | 冻结后删源 run 仍可渲染；EXTERNAL 导出无 IP/命令 |
| **W3** | 内核 SPI + RF 适配 + pytest 内核 + 选择粒度 + 环境探测 | KRN-01..05 | W1 + **G4 已通过**（执行链路定型） | 现有 RF 用例零回退；pytest 样本跑通；选择顺序可解释 |
| **W4** | 解耦断言 + 原生用例导入 + 沙箱 + 预检 + 重导入 | IMP-01..05 | W3 | pytest/RF 各 1 个真实工程导入并执行成功 |
| **W5** | 用户组 + 组授权 + 有效角色 + 超管视图 + 项目模板 + 场景 | IAMX-01..04 | W4（避免权限改造与执行改造交叉） | 越权矩阵 ≥ 60 条全绿；组授权即时生效 |
| **W6** | AI 面板 + CLI 探测 + 上下文 + 建议/执行/评估 + 审计 | AIX-01..05 | W3, W4 | 无 CLI 时降级；有 CLI 时 L1–L3 可用；动作全留痕 |
| **W7** | token 扩展 + 三栏布局 + 组件统一 + 响应式/可访问性 + 截图基线 | UIX-01..04 | W1–W6 | 8 页 × 2 主题基线；1440 利用率 ≥ 78%；1280 无横滚 |

**排序理由**
1. W1 先做端口与骨架：后面所有页面都要截图与走查，入口不稳会反复返工。
2. W2 在 W3 前：报告双实体只依赖 run，先落地可先验证「对外交付」这条价值链。
3. W3 在 W4 前：导入后必须能执行，否则导入只是文件管理器。
4. W5 靠后：`AccessControlService` 被所有域引用，等执行域稳定后再改，减少冲突。
5. W6 依赖 W3/W4 的能力（发起执行、读用例），放后面才能做「真动作」。
6. W7 收口：所有页面就位后统一视觉，避免改两次。

**可并行**：W2 与 W3 由不同人负责时可并行（互不触碰同一批文件）；W7 的 token 部分可提前到 W1 之后做，但布局必须等 W6。

**与既有批次的关系**：W1/W2 可与规划的 B7/B8 并行（只碰 `navigation.ts`、路由、部署脚本与新增表）；W3/W5 会改 `RunService`、`ProjectRunDispatcher`、`AccessControlService`，必须等 B4/B5 对应文件稳定后再开——本文件不重复规划侧的冲突规则，以规划 §4.1 规则 4 为准。

### 3.1 台账与状态口径（与规划 v3.0 对齐）

| 事项 | 规则 |
|---|---|
| 台账 | 34 包**本轮不进** `docs/plan/WBS-STATUS.md`，进度分母仍为 **96**；评审通过后一次性并入 34 行，分母改 **130** |
| 候选期进度 | 只看本文件波次 + `05-TEST-REPORT.md` 的波次归属，不新增第二套计数 |
| 状态 | 五态：未开始 / 进行中 / 待验 / 待现场 / 已完成；每包完成代码 + 单测 + 证据后才可置 `待验`；机器门 + 证据核对通过后由 AI 置 `已完成`（2026-11-06 口径） |
| 排期 | 不排人日与日期；以「进入条件 + 交付判据」推进，门禁 G8 收口 |
| AI 自主度 | 以规划 §7 为准（不在此重复）；C 类包（KRN-01/03、IAMX-02、UIX-04）风险高，AI 判定前须额外做破坏性验证并在证据里写明，人旁路重点复核 |
| 门禁 | 34 包全部完成或按 §9 降级，且 §8 的 G8 前提取证齐全，才可申请 G8 |

---

### 3.2 外部门与样本前置（2026-11-06 现状复核）

依据 `docs/plan/现状复核与文档更新评估-20261106.md`。**波次编号 / 依赖 / 交付判据一项未动**，
本表只把「当前能不能开工」说清楚；「无记录」不等于「未通过」。

| 波次 | 外部门 | 样本前置 | 当前可否开工 |
|---|---|---|---|
| W1 | 无（不依赖既有批次） | **PRD 评审通过 + 34 包并入 WBS** | 否：评审未过、分母仍 96 |
| W2 | **G5 已通过** | C5 对外报告字段白名单 | 否：G5 无书面通过记录、C5 缺 |
| W3 | **G4 已通过** | C2 pytest 工程、C3 RF 工程、C4 venv 白名单 | 否：G4 无书面通过记录、C2/C3 缺 |
| W4 | W3 | C2 / C3 真实工程（导入后可执行） | 否：前置未满足 |
| W5 | W4 | C7 组织模型（D4 已定案）、C8 场景边界（D5 已定案） | 否：前置未满足（裁决类已闭环） |
| W6 | W3, W4 | C6 本机 AI CLI 非交互探测 | 否：前置未满足 |
| W7 | W1–W6 | 8 页 × 2 主题截图基线 | 否：前置未满足 |

**C1–C10 落点**（条件本体见 `v2.0-裁决建议表-20261106.md` §2）：

| 条件 | 现状 | 落点波次 | 不满足时的处置 |
|---|---|---|---|
| C1 8001 语义 | D1 已定案 A | W1 | 已闭环 |
| C2 pytest 真实工程 | 缺 | W3 / W4 / G8 第 4 条 | 等大师提供；不造玩具样本，相关 TC 记 NOT-RUN |
| C3 RF 真实工程 | 缺 | W3 / W4 / G8 第 4 条 | 同 C2 |
| C4 pytest venv 白名单 | 未定（D2 关联） | W3 | 专用 venv 与允许插件清单落定前不开 W3 |
| C5 字段白名单 | 缺 | W2 / G8 第 6 条 | EXTERNAL 导出不得对外 |
| C6 AI CLI 非交互 | 未探测 | W6 | 学徒 W6 前探测留日志；无 CLI 时按 R6 降级 |
| C7 组织模型 | D4 已定案 A | W5 | 已闭环 |
| C8 场景与用例集边界 | D5 已定案 A（一层、无 DAG） | W5 | 已闭环 |
| C9 备份覆盖新表 | V8_x 未逐波登记 | W2 起每波 | 每波同步登记，否则不回退 |
| C10 不改 WBS 台账 | 已满足（仍 96） | 并入 34 包前 | 评审通过后才改分母 130 |

> **不得跳步**：PRD 评审通过（大师）→ 34 包并入 WBS（96 → 130）→ 才开 W1。
> W2 / W3 仍分别需 G5 / G4；`G-授权` 与 ADR-0013 禁跑令不因本次复核解除。

## 4. 工作包

> 完成定义（DoD）：代码 + 单测 + 至少 1 条集成/实测 + `docs/evidence/v2.0/<包>/README.md`（命令、原始输出、结论）+ 文档更新。
> 证据目录统一 `docs/evidence/v2.0/`，不写「已验证」而无输出。

### 4.1 ENVX 端口与交付（FR-v2.0-53 ~ 55）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| ENVX-01 | 静态承载 + 反代（nginx 或 Spring 静态 + 反代择一，需 ADR）+ SPA fallback + 缓存策略 + 报告打印 base URL 改 8001 | — | 深链接刷新 200；`/api`、`/ws` 通；资源指纹命中 | `curl -I` 三组 + 浏览器截图 |
| ENVX-02 | `deploy.sh` / `rollback.sh` / systemd unit / `/healthz` / `/api/health` / 端口矩阵文档 + TLS 样例 | ENVX-01 | 一条命令部署并自检；回滚回上一版；矩阵三处一致（设置页/关于页/手册） | 部署日志 + 回滚日志 |

### 4.2 NAV 菜单与系统页（FR-v2.0-07 ~ 13）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| NAV-01 | `navigation.ts` 重构为 6 域 + 能力驱动过滤 + 项目选择器上移 + 路由守卫补 403 页 | — | 4 角色菜单快照；直达无权限路由见 403 | vitest 快照 + 截图 |
| NAV-02 | 系统设置：平台/项目分栏、字段校验、审计 | NAV-01 | 非法值拒绝；变更留痕；并发数生效 | 接口测试 + 审计查询 |
| NAV-03 | 关于页（构建信息注入）+ 版本迭代页（读 CHANGELOG） | NAV-01 | 版本/commit 与构建一致；CHANGELOG 四类齐全 | 构建产物对照 |
| NAV-04 | 命令面板 + 收藏 + 最近访问 + 折叠持久化 | NAV-01 | `Ctrl+K` 可搜中英文；最近 5 条；刷新保留 | 组件测试 + 走查 |

### 4.3 RPTX 报告（FR-v2.0-01 ~ 06）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| RPTX-01 | `test_report` 实体 + 服务 + 状态机（DRAFT/FROZEN/REVOKED/ARCHIVED）+ 权限 | V8_3 | 非法跃迁拒绝；跨项目 403；列表与详情可分页 | 单测 + 接口测试 |
| RPTX-02 | 快照冻结：物化 JSON/HTML + hash + 版本号 + 源 run 摘要 | RPTX-01 | 删源 run 后仍渲染；重复冻结幂等；篡改 hash 不匹配告警 | 端到端实测 |
| RPTX-03 | 测试报告渲染（对外视图）：结论、通过率、失败分布、覆盖、趋势 | RPTX-02 | 聚合口径与测试计划一致；跳过单列；趋势对比可用 | 截图 + 数值断言 |
| RPTX-04 | 执行报告渲染（对内重构）：关键字树、命令、原始输出、产物、重试 | — | 现有报告能力不回退；大 run（5k 结果）不卡死 | 回归 + 性能计时 |
| RPTX-05 | 导出 HTML/PDF/DOCX + 模板 + 水印 + 保留策略 | RPTX-03 | 三格式一致；EXTERNAL 无敏感字段；过期可清理 | 导出文件抽样 + 字段扫描 |

### 4.4 KRN 双内核（FR-v2.0-14 ~ 22）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| KRN-01 | SPI + 注册表 + `NormalizedRun` + 结果归一 + ArchUnit 边界规则 | V8_1 | 两内核实现同一契约；边界规则测试通过 | 单测 + ArchUnit |
| KRN-02 | `RobotKernel`：包装现有 `PabotCommandBuilder` / `PabotOrchestrator`，命令逐字等价 | KRN-01 | 同参数生成命令与 v1.0 完全一致；既有 RF 用例全绿 | 命令 diff + 全量回归 |
| KRN-03 | `PytestKernel`：json-report 主通道 + junitxml 兜底 + 参数化展开 + xfail 映射 | KRN-01 | 5 类状态映射正确；插件缺失降级不失败 | 真实样本执行日志 |
| KRN-04 | 选择粒度：项目/用例集/用例 + 生效来源展示 + 覆盖标记 | KRN-01 | 四级顺序可解释；一 run 一内核；历史不可变 | 接口测试 + UI 截图 |
| KRN-05 | 环境探测 + 能力矩阵 + 进程树终止 + 重试/超时统一 | KRN-01 | 缺环境 422 带指引；取消无残留子进程；重试留 attempt | `ps` 前后对比 + 实测 |

### 4.5 IMP 解耦与导入（FR-v2.0-23 ~ 31）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| IMP-01 | 解耦：schema 断言（用例无设备外键）+ `case_source` 模型 + 平台/项目可见性 | V8_2 | 断言测试通过；PUBLIC 引用不复制；副本可独立演进 | 元测试 + 数据断言 |
| IMP-02 | 上传与沙箱：zip/目录、路径校验、限额、只读落盘、校验和、版本 | IMP-01 | 7 类攻击样本全部拒绝；正常包落盘结构正确 | 安全用例（正/负各 7） |
| IMP-03 | pytest 导入适配：conftest/fixture、包结构、`--collect-only` 预检 | IMP-02, KRN-03 | 真实工程清单与本地 `--collect-only` 数量一致 | 数量对账 |
| IMP-04 | RF 导入适配：resource 解析、suite 结构、`--dryrun` 预检 | IMP-02, KRN-02 | 套件树与目录一致；dryrun 错误带行号 | 数量对账 |
| IMP-05 | 预检报告 + 敏感信息扫描 + 重导入 diff/冲突 | IMP-03, IMP-04 | 三态判定准确；diff 三类清单；冲突不覆盖 | 报告样例 + 实测 |

### 4.6 IAMX 组织（FR-v2.0-32 ~ 39）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| IAMX-01 | 用户组 CRUD + 成员管理 + 前端页面 | V8_4 | 组增删改查；成员去重；删除组不删用户 | 接口测试 |
| IAMX-02 | 组-项目授权 + `EffectiveRoleResolver` 单点实现 + 缓存失效 | IAMX-01 | 优先级正确；移除即时生效；成员列表显示来源 | 单测 4 角色 × 场景 |
| IAMX-03 | 超管全局视图 + 项目切换 + 跨项目统计 | IAMX-02 | 全局数据完整；非超管 403；切换上下文一致 | 接口 + 截图 |
| IAMX-04 | 项目模板/归档 + 测试场景实体与编排 | IAMX-03 | 模板初始化 3 项；归档禁跑；场景可执行 | 实测 + 证据 |

### 4.7 AIX 智能体（FR-v2.0-40 ~ 46）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| AIX-01 | 右侧抽屉骨架 + 开关 + 宽度记忆 + 与布局共存 | NAV-01 | 开合不影响内容区；关闭后无入口 | 截图 + DOM 量测 |
| AIX-02 | CLI 探测 + 配置档 + 进程管理（超时/终止/编码） | V8_6 | 5 类 CLI 探测结果准确；无 CLI 提示安装 | 探测日志 |
| AIX-03 | 上下文打包 + 脱敏 + 截断 | AIX-02 | 不含密钥/IP；超限提示；可复现 | 上下文样例（脱敏后） |
| AIX-04 | 动作编排：建议 / 发起执行（二次确认）/ 效果评估 | AIX-03, KRN-04 | 越权发起被拒；建议不落盘；评估摘要可用 | 审计记录 + 实测 |
| AIX-05 | 审计 + 降级 + 一键终止 + 主功能隔离 | AIX-04 | 断 CLI 主功能零影响；动作全留痕 | 故障注入 |

### 4.8 UIX 界面体系（FR-v2.0-47 ~ 52）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| UIX-01 | token 扩展：字体 6 级、间距 4px、密度两档、深色覆盖 | — | 硬编码 0 处；对比度达标；深色无遗漏 | `check_tokens.py` + 截图 |
| UIX-02 | 三栏布局 + 折叠 + 抽屉共存 + 利用率 | UIX-01, AIX-01 | 1440 利用率 ≥ 78%；抽屉打开无横滚 | Playwright 量测 |
| UIX-03 | 表格/表单/抽屉/确认框统一 + 四态 | UIX-02 | 8 页四态齐全；危险操作二次确认 | 页面清单核对 |
| UIX-04 | 响应式 + 可访问性 + 截图基线 | UIX-03 | 1280 无横滚；键盘可达；16 张基线入库 | Playwright + 截图 diff |

---

## 5. 关键实现约定

### 5.1 pytest 结果归一

| pytest 状态 | 平台状态 | 备注 |
|---|---|---|
| passed | PASSED | — |
| failed | FAILED | 断言失败归 `ASSERTION`；collection error 归 `CASE_ERROR` |
| error（setup/teardown） | FAILED | 归 `ENVIRONMENT` 或 `CASE_ERROR`，看阶段 |
| skipped | SKIPPED | 不计入通过率分母 |
| xfailed | SKIPPED（`expected_failure=true`） | 单列统计 |
| xpassed | FAILED（`unexpected_pass=true`） | 严格口径，可配置 |
| 超时 | FAILED（`TIMEOUT`） | 由平台超时监控写入，不由 pytest 插件 |

参数化：`nodeid` 相同但 `params` 不同 → `external_id = nodeid + "#" + params_hash`，`parameters_json` 存参数。

### 5.2 导入沙箱

- 解压到 `tmp/import-<uuid>`，逐条校验后再原子移动到 `sources/<sourceId>/v<n>/`。
- 拒绝：`..`、绝对路径、符号链接、设备文件、超限、非白名单可执行。
- 只读：落盘后 `chmod -R a-w`（或应用层禁写），项目内副本另存 `workspace/`。
- 预检在**独立 venv** 内跑 `--collect-only` / `--dryrun`，超时 120s，网络禁用的前提下执行。

### 5.3 有效角色

单点实现 `EffectiveRoleResolver`，禁止各处自行拼装；`AccessControlService` 改为调用它；缓存键含 `(userId, projectId, membershipVersion)`，任何组织变更 `bump` 版本。

### 5.4 AI 进程

- 以 `ProcessBuilder` 启动，`directory=项目工作区`，`environment` 白名单（PATH/HOME/LANG/TZ/TERM）。
- 输出按行读，SSE 推送；最大会话 30 分钟；空闲 5 分钟终止；可一键 kill 进程树。
- 所有「写动作」走 `ActionGate`：校验权限 → 二次确认 → 执行 → 审计，四步缺一不可。

---

## 6. 特性开关与灰度

| 开关 | 默认 | 作用 |
|---|---|---|
| `feature.report.v2` | on（W2 后） | 新报告入口；关闭则回旧页面 |
| `feature.kernel.pytest` | off → 试点项目 on（W3 后） | pytest 内核可选；关闭时项目设置不显示 |
| `feature.ai.panel` | off（W6 后按需开） | 右侧面板入口 |
| `feature.group` | off（W5 后开） | 组授权入口；关闭时按原项目成员逻辑 |

开关值落 `system_setting`，变更需管理员并留痕；**不做按用户灰度**（自用规模，过度设计）。

---

## 7. 迁移与发布

1. **备份**：`mysqldump` 全库 + `frontend/dist` + `docs/evidence`。
2. **迁移演练**：空库从 V1 全量迁到 V8_7；生产库副本迁一次并跑 `db-audit`。
3. **先部署后开开关**：W1 只换入口与菜单，不改数据；W2 起每波迁移前先备份。
4. **回滚**：应用回上一镜像 + `db/down/V8_x`（只回滚本波新增，不跨波回滚）。
5. **不兼容点**：`test_result.kernel` 置 NOT NULL 后，旧版本应用写库会失败 → 回滚必须同时回应用与 schema，写进回滚手册。

---

## 8. 质量门禁

每批合并前跑八步门禁（模板 `docs/evidence/v1.0/longhist/gates.sh`）：

1. `python3 tools/subset_font.py`
2. `cd frontend && npx vitest run`
3. `npx vue-tsc --noEmit`
4. `npx vite build`
5. `cd backend && mvn -o -B test`（含 `-Pit` 在集成批）
6. `bash scripts/lint.sh`（secret-scan / eslint / compile / 14 个 check_*）
7. `python3 tools/check_release_docs.py`
8. `python3 tools/check_wbs_consistency.py`

新增守门建议（随包落地）：`check_kernel_boundary.py`（禁止执行域出现 pabot/pytest 字样）、`check_report_sensitive.py`（EXTERNAL 模板字段扫描）。

### 8.1 G8 申请前置（v3.0）

G8 判定本体是 **10 条**，唯一权威清单在 `PRD.md` §6（与规划附录 B·G8 逐条对齐），此处不另立一套。申请前必须同时满足：

1. 上面八步门禁全绿，且后端/前端单测不低于基线；
2. `TEST-PLAN.md` 的 67 条 TC **无 `NOT-RUN`**（有则先补测，或按 §9 降级并把降级理由写进 `05-TEST-REPORT.md`）；
3. 34 包在证据目录 `docs/evidence/v2.0/<包>/README.md` 各有一份「命令 + 原始输出 + 结论」；
4. 双内核矩阵 M1–M10 有实跑记录；`KRN-02` 命令 diff 为空；
5. 越权矩阵 ≥ 60 条、真实工程导入、冻结快照、8001 链路的证据可点开复核。

---

## 9. 风险与降级

| 风险 | 触发信号 | 降级方案 |
|---|---|---|
| pytest 归一失真 | 同一含 pass/fail/skip/xfail/error 的样本工程，两内核逐条比对出现差异且两轮修不掉 | **B9 停在 W2**：W3 之后波次不启动；**不允许降级成单内核交付**；补齐映射与人工抽样复核后重开 W3（规划 §4.3 T4） |
| 导入成功率低 | 真实样本可执行率 < 60% | 预检前置、给出适配清单；不承诺零改 |
| 组授权越权 | 越权矩阵出现 1 条失败 | 关闭 `feature.group`，回退项目成员模型 |
| 8001 承载不稳 | 健康检查连续失败 | 回滚部署脚本；8081 直连仍可用 |
| AI CLI 不可用 | 探测全失败 | 面板只显示安装指引；主功能不受影响 |
| 报告快照膨胀 | 单份 > 20 MB | 快照只存结构化数据 + 引用制品；HTML 按需渲染 |

---

## 修订记录

| 日期 | 版本 | 改了什么 | 为什么改 |
|---|---|---|---|
| 2026-11-06 | v1.0 | 初版：架构、14 表迁移、7 波次、34 包、5 项实现约定、4 个开关、门禁与降级 | 承接 PRD 55 条 FR |
| 2026-11-06 | v1.1 | ① §3 波次依赖补外部门：W2 需 G5、W3 需 G4，明确 W1 无前置；② 新增 §3.1「台账与状态口径」——34 包候选、分母 96、评审后 130、五态、不排日期、C 类包；③ 新增「与既有批次的关系」段（W1/W2 可并行，W3/W5 等 B4/B5 稳定）；④ 新增 §8.1「G8 申请前置」，判定本体统一指向 PRD §6；⑤ §9 把 pytest 失真降级从「只留 RF」改为「停在 W2、禁止降级单内核」 | 与规划 v3.0 的 B9 波次、G8 判定与 T4 规则保持同一口径；原「只留 RF」的降级与「双内核是重点特性」相矛盾 |
| 2026-11-06 | v1.2 | 补登记：D1–D10 十项裁决经大师两轮问卷勾定、全按推荐值 A（此前只以末尾补记留痕） | 与 PRD / TEST-PLAN 的 v1.2 对齐，修订记录不留空档 |
| 2026-11-06 | v1.3 | ① §3 新增「3.2 外部门与样本前置」：逐波写清外部门 / 样本前置 / 当前可否开工，并列 C1–C10 落点；② 明写「PRD 评审通过 → 34 包并入 WBS → 才开 W1」不可跳 | 按 `现状复核与文档更新评估-20261106.md` §5 回写；**波次编号 / 依赖 / 交付判据 / G8 十条 / C 类包风险要求一项未动** |

---

*补记（2026-11-06，不升版本号）：§3 / §3.1 的状态判定口径由「人裁决后才 `已完成`」改为「AI 验收放行」，
与 WBS 13:43 及大师裁决一致；授权见 `docs/plan/决策纪要-20261106.md`。波次依赖（W2 需 G5、W3 需 G4）、
G8 十条判定、C 类包风险要求**一项未动**。*

*补记（2026-11-06，不升版本号）：v2.0 的 D1–D10 十项裁决已由大师两轮向导问卷勾定，**全取 `01-ASSESSMENT.md` §6 推荐默认值 A**（D5 仅一层编排不做 DAG；D10 设置分级）。本文件的 **验收判据 / 波次依赖 / G8 十条判定一项未动**，状态仍为「待评审」——**PRD 评审通过归大师**，勾定不等于评审通过；WBS 分母仍 96、34 个候选包未并入、W1 未开工。权威记录见 `docs/plan/v2.0-裁决建议表-20261106.md` §6 与 `docs/plan/决策纪要-20261106.md` §8。*

| 2026-11-06 | v1.4 | AI 书面评审**有条件通过**（大师 15:23 一次性授权）；34 包并入 WBS（96→130），W1 六包开工 | 人工复核为最终确认；W2–W7 仍受 G4/G5 与 C2/C3/C5 约束 |

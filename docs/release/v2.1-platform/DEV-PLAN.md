# v2.1 提效与扩展 · ④ 开发计划

**文档编号** RA-REL-2026-021-D ｜ **版本** v2.1-platform ｜ **日期** 2026-11-06 ｜ **状态** 已实施完成（2026-11-07：13 包全落地，逐包证据见 `docs/evidence/v2.1/`；发版门 G4/G5 未判）
**上游** `PRD.md`（17 条 FR / 8 条 NFR / 9 条验收）｜ **下游** `TEST-PLAN.md`
**口径** 13 个候选工作包（ORCH 3 / TPLX 3 / PUBX 2 / NODX 4 / SMPL 1）｜ **不进** `WBS-STATUS.md`，分母仍 130
**并行关系** 与 `docs/release/v2.0-platform/06-收口执行计划-20261106.md`（层 A/B）双线推进，互不为前置

---

## 1. 总体结构

### 1.1 三个新增层次

| 层 | 落点 | 不碰什么 |
|---|---|---|
| **编排层** | `execution_plan` 之上加「计划组」；复用既有 `execution_plan_dependency` 与 `PlanDependencyGraph` | 不改 `execution_plan` 既有字段语义；不加多层嵌套（沿用裁决 D5） |
| **呈现层** | 报告模板实体 + 第四种导出格式（xlsx）；模板只从 C5 白名单字段里选 | 不改 `test_result` / `run` 结构；不改已冻结快照的渲染结果 |
| **算力层** | 执行节点注册表 + 心跳 + 指定节点执行 + 结果回收 | 不改 `ProjectRunDispatcher` 的本机调度语义（节点是**追加**通路，不是替换） |

### 1.2 模块边界（ArchUnit 约束）

| 规则 | 说明 |
|---|---|
| `orchestration.plan` 不得依赖 `api.*` | 编排域只依赖仓储与领域模型，控制器反向调用 |
| `report.template` 不得引用 `db.migration` 之外的敏感字段常量 | 白名单常量单点定义在 `ReportFieldWhitelist`，模板域只读它 |
| `node.*` 不得依赖 `infrastructure.pabot` | 执行端 agent 通过 `KRN` 契约拿命令，不直连 pabot 类 |
| `node.*` 的出站调用必须过 `NodeChannel` 单点 | 便于统一加签名、超时、重试上限 |

---

## 2. 数据模型变更

> 迁移从 `V9_1` 起（现为 `V8_6` / 60 表 / 30 迁移）。**每个迁移必须带 `db/down/` 脚本**，且 down 实跑过一次才算完成。

| 迁移 | 表 / 变更 | 用途 | down |
|---|---|---|---|
| `V9_1__plan_group.sql` | `execution_plan_group`（id / project_id / name / status / created_* ）；`execution_plan_group_item`（group_id / plan_id / sort_order / UNIQUE(group_id, plan_id)） | 计划组与组内顺序 | 删两表 |
| `V9_2__report_template.sql` | `report_template`（id / project_id NULL=平台级 / name / visibility / field_json / version）；`report_template_field`（template_id / field_key / sort_order / label_override）；`test_report` 加 `template_id` / `template_version` | 模板可配置；冻结时记录所用模板 | 删两表 + 删两列 |
| `V9_3__case_source_version.sql` | 平台用例集加 `content_hash` / `asset_version`；项目副本加 `synced_asset_version` / `fork_state` | 「有新版本」检测与分叉态 | 删列 |
| `V9_4__execution_node.sql` | `execution_node`（id / name / base_url / token_hash / status / last_heartbeat_at / capabilities_json / max_parallel）；`run` 加 `node_id`（NULL=本机） | 第二执行节点与 run 归属 | 删表 + 删列 |

**约束口径**

- `execution_node.token_hash` 只存哈希（`NFR-v2.1-04`），明文只在注册时返回一次。
- `report_template.field_json` 存**字段键数组**，不存表达式、不存 SQL、不存 HTML。
- `run.node_id` 为 NULL 的历史数据语义 = 本机执行，**不做回填**（回填会伪造历史事实）。

---

## 3. 波次计划

| 波次 | 内容 | 包 | 进入条件 | 出口判据 |
|---|---|---|---|---|
| **W8** | 示例用例集 + 计划组 + 依赖图 + 失败聚类 | SMPL-01, ORCH-01..03 | 无（SMPL-01 可立即开工；ORCH 需 F1/F2 实体已在） | 五态示例用例可跑；组内顺序与自动建边正确；同一输入两次聚类结果一致 |
| **W9** | 报告模板 + XLSX + 四格式一致性 | TPLX-01..03 | **层 A 的 A8（报告真实链路抽样）完成**（避免同一批 `ExportService` 文件打架） | 模板选中字段可导出 xlsx；EXTERNAL 四格式扫描 0 命中；改模板不影响已冻结报告 |
| **W10** | 公用资产升级闭环 | PUBX-01..02 | W9 出口（同属对外呈现面，顺序排开减少回归面） | 内容未变不误报；三类差异正确；同步与分叉两态可辨 |
| **W11** | 第二执行节点 | NODX-01..04 | W8 出口 + `ENVX-02` 部署脚本可用 + **C 类人工主导**（节点通信方案由大师确认） | 注册/心跳/指定执行/回收端到端 1 条；离线拒绝 1 条；命令 diff 为空 |

**排布理由**

1. **SMPL-01 排第一**：它是 W8–W11 的回归底座，且本机零依赖（pytest 那份随 A1）。
2. **W9 让开 A8**：`ExportService` 是两边都要改的文件，同一时间改必然冲突。
3. **W11 排最后**：唯一 C 类包，且需要第二台真机才能验收；开发期用本机第二端口模拟。

---

## 4. 工作包

> 完成定义（DoD）：代码 + 单测 + 至少 1 条集成/实测 + `docs/evidence/v2.1/<包>/README.md`（命令、原始输出、结论）+ 文档更新。
> 证据目录统一 `docs/evidence/v2.1/`；不写「已验证」而无输出。

### 4.1 SMPL 示例用例集（FR-v2.1-17）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| SMPL-01 | 5–10 条示例用例：RF 3–5 条（覆盖 pass / fail / skip）、pytest 3–5 条（覆盖 pass / fail / xfail / error）；不映射任何真实业务；每套件带 metadata 六必填 | — | RF `--dryrun` 0 `[ ERROR ]`；五态齐全；metadata 六必填齐 | dryrun 输出 + 实跑 `output.xml` |

### 4.2 ORCH 编排与聚类（FR-v2.1-01 ~ 05）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| ORCH-01 | 计划组实体 + 成员排序 + 批量加入自动建边（复用 `PlanDependencyGraph` 环检测，成环拒绝并指出环上计划） | V9_1 | 3 计划成组产生 2 条边；重复加入幂等；成环被拒且报出计划名 | 单测 + 接口测试 |
| ORCH-02 | 依赖图只读视图 + 关键路径 + 预计时长（`timeout_seconds` 求和，标注估算） | ORCH-01 | 50 节点渲染 ≤ 1s；关键路径高亮正确；孤立点有区分 | Playwright 计时 + 截图 |
| ORCH-03 | 失败聚类 + 建议重跑清单：**复用既有 `FailureClassifier` 作一级类**，在类之下按归一化文本建二级簇（簇键：`failure_reason` 优先，缺失取 `failure_message` 归一化首行） | ORCH-01 | 1 万条输入 ≤ 3s；同一输入两次划分一致；清单可直接发起 run | 基准脚本 + 两次结果 diff |

### 4.3 TPLX 模板与导出（FR-v2.1-06 ~ 09）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| TPLX-01 | 模板实体 + 字段白名单勾选 + 顺序 + 标题覆盖 + 双拦截（接口 + 前端） | V9_2 | 白名单外字段无法选中（两侧各 1 条负向）；顺序持久化；平台级模板可被多项目引用 | 接口负向 + 组件测试 |
| TPLX-02 | 冻结时按模板渲染快照 + xlsx 分支（POI） | TPLX-01, RPTX-02 | 冻结后改模板不影响已冻结报告；xlsx 可被 Excel/WPS 打开；数值列是数字不是文本 | 端到端 + 文件抽样 |
| TPLX-03 | 四格式一致性校验（HTML/PDF/DOCX/XLSX 字段集合比对） | TPLX-02 | 不一致时导出报错并列出缺失字段；四格式抽样各 1 份 | 校验脚本 + 差异样例 |

### 4.4 PUBX 资产升级（FR-v2.1-10 ~ 12）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| PUBX-01 | 版本标识（显式版本号 + 内容 hash）与引用方检测 | V9_3 | 内容未变不出新版本；变更后引用方列表出现提示；版本可追溯 | 单测 + 数据断言 |
| PUBX-02 | 差异对比（新增/修改/删除三类）+ 同步或保持分叉 + 审计 | PUBX-01 | 三类差异分类正确；同步后版本对齐；分叉后副本不动且标记「已分叉」 | 端到端 + 审计查询 |

### 4.5 NODX 第二执行节点（FR-v2.1-13 ~ 16）

| 包 | 内容 | 依赖 | 验收 | 证据 |
|---|---|---|---|---|
| NODX-01 | 节点注册（令牌只存哈希）+ 心跳 + 在线/离线判定 + 能力上报 | V9_4 | 3 次缺心跳标 `OFFLINE`；令牌不入日志；节点列表显示内核能力与占用 | 心跳日志 + 日志抽样 |
| NODX-02 | 指定节点执行 + 离线拒绝 + 独立并发上限 | NODX-01 | 离线节点发起被拒且提示原因（不回落本机）；未指定走本机；并发独立计数 | 接口测试（正/负） |
| NODX-03 | 执行端 agent：复用 `KRN.buildCommand`，产物与 `output.xml` 回传 | NODX-02, KRN-02 | 同参数命令 diff 为空；产物完整回传；agent 崩溃主控可判失败 | 命令 diff + 端到端 |
| NODX-04 | 节点页 + 离线口径文档化（本版：拒绝发起，不排队） | NODX-02 | 节点页四态齐全；口径写入文档并被 TEST-PLAN 引用 | 截图 + 文档 |

---

## 5. 关键实现约定

### 5.1 失败聚类簇键（最容易做成噪音，先定死）

**不重写分类器**：`FailureClassifier` 已实现 `CANCELED > ENV > INFRA > ASSERT` 四类判定（关键词 + 正则规则可配）。
本版只在「类」之下加一层「簇」，两级都保留，界面按 类 → 簇 下钻。

| 级 | 取值 | 归一化规则 |
|---|---|---|
| 一级（复用） | `FailureClassifier.classify(...)` 的输出 | 不改判定逻辑、不改优先级 |
| 二级（新增） | **只取 `test_result.failure_msg`** 的首行 | 去路径（`/…/x.py` → `<path>`）→ 去 IP 与端口（`<ip>`）→ 去数字（`\d+` → `<n>`）→ 折叠空白 → 取前 40 字符 |
| 兜底 | 二者皆空 | 固定簇 `未分类`（一级类仍由分类器给） |

> **v1.1 订正（ORCH-03 实测）**：原文写「`failure_reason` 优先，缺失取 `failure_message` 首行」，隐含假设这两个字段在用例级。
> 实测 `test_result` 只有 `failure_msg`；`failure_reason` / `failure_class` / `failure_message` 都在 **`run` 表**，是整轮结论。
> 簇必须按用例聚合，所以簇键**只取 `test_result.failure_msg`**，run 表字段退化为整轮上下文展示（`runStatus` / `runFailureClass`）。
> 详见 `docs/evidence/v2.1/ORCH-03/README.md` §4.1。

- **不得随机**：不做 k-means、不做 embedding，纯确定性规则（`FR-v2.1-04` 要求两次结果逐字节一致）。
- 簇名展示「一级类 / 二级簇键 + 条数 + 占比」，不生成自然语言摘要（那是 AI 的事，本版不做）。

### 5.2 模板安全边界

| 允许 | 禁止 |
|---|---|
| 从白名单字段里勾选显示/隐藏 | 输入任意字段名 |
| 调整字段顺序 | 写表达式 / 写 HTML / 写 SQL |
| 覆盖显示标题文案 | 覆盖字段取值逻辑 |
| 选择可见性级（INTERNAL / EXTERNAL） | 自建可见性级 |

白名单常量单点定义（`ReportFieldWhitelist`），模板域只读；**新增字段必须先改白名单**，否则模板选不到。

### 5.3 节点通道

- 主控 → 节点：`POST /node/run`（带签名任务 JSON），节点 → 主控：`POST /node/heartbeat`、`POST /node/result`。
- 出站一律过 `NodeChannel`（统一超时 10s、重试 1 次且重试换新 HttpClient、失败即判节点离线，不重试到底）。
- 本机执行路径**不经过** `NodeChannel`，保持 v2.0 语义不变（避免为了统一而引入回归）。
- **离线口径（NODX-04 落文档）**：指定节点发起时，节点只要不是 `ONLINE`（NEW / OFFLINE /
  DISABLED / 心跳缺席判离线）一律**当场拒绝**并给原因——**不排队、不重投、不回落本机执行**；
  投递发出后节点失联的 run 由监视器判 FAILED（`node_agent_lost`），同样不排队不重投。
  本口径被 TEST-PLAN TC-v2.1-25 引用为验收判据。

### 5.4 与收口版的文件冲突规避

| 冲突文件 | 收口版用途 | v2.1 用途 | 处置 |
|---|---|---|---|
| `ReportExportService.java` | A8 补真实导出抽样 | TPLX-02 加 xlsx 分支 | W9 不早于 A8 完成 |
| `scripts/gates.sh` | B4 收敛为单份 + 加第 13/14 步 | TPLX-03 的四格式扫描要进门禁 | B4 先做，v2.1 只往已收敛的单份里加步骤 |
| `db/migration/` | 收口版不动 schema（B5 除外） | V9_1..V9_4 | B5 与 V9_x **不得同批发布** |

---

## 6. 特性开关与灰度

| 开关 | 默认 | 作用 |
|---|---|---|
| `feature.plan.group` | off | 计划组与自动建边；关闭后 UI 不显示入口，接口 404 |
| `feature.report.template` | off | 模板配置与 xlsx 导出；关闭后回落 v2.0 两套固定视图 |
| `feature.asset.upgrade` | off | 新版本提示与差异；关闭后 PUBLIC 引用行为与 v2.0 一致 |
| `feature.node.exec` | off | 第二节点；关闭后仅本机执行，节点页只读 |

灰度口径：每个开关独立、可单开；**不做百分比灰度**（单实例内网自用，按项目/环境整体开关即可）。

---

## 7. 迁移与发布

1. 迁移顺序固定 `V9_1 → V9_2 → V9_3 → V9_4`，每步单独可回滚。
2. 发布前置：机器门全绿 + `db-audit` PASS + 备份（`scripts/rollback.sh` 全库 dump）。
3. 回滚：先 `rollback.sh` 回上一版程序，再按需跑 `db/down/V9_x`；**down 只回本次变更**。
4. 演练授权：按拍板第 3 条，允许在 dev 库做一次 `deploy → health → rollback → health` 演练，前后强制备份。
5. 发版 / tag / 破坏性变更**归大师**，本文件不预设版本号与日期。

---

## 8. 质量门禁

**每包必过（DoD 机器部分）**

- [ ] 后端 `mvn -o -B test` 全绿且条数不减（基线 780）
- [ ] 前端 `vitest` 全绿且条数不减（基线 239）；`vue-tsc` 无错
- [ ] `scripts/lint.sh` PASS（含 B4 收敛后的全部步骤）
- [ ] `db-audit` PASS；新增表纳入备份
- [ ] 证据目录 `docs/evidence/v2.1/<包>/README.md` 含命令与原始输出

**v2.1 验收前置**

- [ ] 14 步机器门 `FAILS=0`（第 13 步 `css_vars` 于 ORCH-02 接入、第 14 步 `report_consistency` 于 TPLX-03 接入，见 §10.0）
- [ ] 迁移 down 实跑过一次
- [ ] 命令 diff 为空（`tools/kernel_cmd_diff.py`）

---

## 9. 风险与降级

| 风险 | 降级方案 | 降级后影响 |
|---|---|---|
| 簇键口径在实际数据上碎片化 | 退化为「按 `failure_reason` 精确分组」，不做归一化 | 簇数变多，但仍可用；FR-v2.1-04 判据改为「同 reason 必同簇」 |
| 无第二台真机 | 用本机第二端口（`:8082`）模拟节点跑 NODX 全部开发期验收 | `NODX-03/04` 记 `待现场`，与 v2.0 的 6 个待现场同口径 |
| POI 写 xlsx 内存超限 | 改用 SXSSF 流式写 | 无功能影响，只影响实现方式 |
| 模板配置与 C5 白名单冲突 | 先只做「显示/隐藏 + 顺序」，不做标题覆盖（`FR-v2.1-06` 降为 P1） | 客户模板表头对齐延后，但敏感字段零外泄 |
| W9 与 A8 仍冲突 | W9 整体后移到 A8 之后一个波次 | 触发 v2.1 整体顺延，不影响 P-1/P-2 |

---

## 10. 逐包开工单（v1.1 新增）

> 每个包给五件事：**改哪里 / 做什么 / 跑什么 / 判什么 / 证据放哪**。文件路径全部按当前位置核对过，不写「待定」。
> 五段式记法：**改**（触碰文件）· **做**（步骤）· **跑**（命令）· **判**（判据）· **证**（证据）。

### 10.0 通用约定

- **证据根目录**：`docs/evidence/v2.1/<包>/README.md`。v2.0 的历史证据散在 `docs/evidence/v1.0/w2-report…w7-design`，本轮起**按版本分根**，旧件不搬迁（搬了会断掉既有引用）。
- **门禁**：`bash docs/evidence/v1.0/policy-20261106/gates.sh`（14 步）；B4 收敛后改跑 `bash scripts/gates.sh`。
- **单跑一条测试**：后端 `cd backend && mvn -o -B test -Dtest=<类名>`；前端 `cd frontend && npx vitest run <路径>`；editor 单独 `cd packages/editor && npx vitest run`。
- **迁移三件套**：`bash scripts/db-migrate.sh`（正向）、`bash scripts/db-undo.sh`（回退）、`bash scripts/db-audit.sh`（审计）。
- **界面取图**：优先复用 `node docs/evidence/v1.0/w7-design/capture.mjs`（逐页截图基线）。
  **例外**：判据里有「首屏耗时」或「真实 computed style」的包，允许在本包证据目录下写专属
  `capture.mjs`——通用脚本没有首屏探针，也不量渲染结果，拿它当证据等于没验
  （ORCH-02 就是这么发现「节点渲染成黑块」的，见 `docs/evidence/v2.1/ORCH-02/README.md` §4）。
- **一包一提交**：不把两个包的改动混进同一提交，否则回归红了分不清是谁。

### 10.1 SMPL-01 · 示例用例集（W8，最先开工）

- **改**：新建 `fixtures/samples/v2.1/robot/sample_states.robot`、`fixtures/samples/v2.1/pytest/test_sample_states.py`、`fixtures/samples/v2.1/README.md`
- **做**：① RF 侧 3–5 条覆盖 pass/fail/skip；② pytest 侧 3–5 条覆盖 pass/fail/xfail/error；③ 每条带 metadata 六必填；④ README 写明「不映射业务、不碰 DUT、不联网」
- **跑**：`~/rf-stack/.venv/bin/robot --dryrun fixtures/samples/v2.1/robot`，再实跑一遍；pytest 侧待 A1 完成后 `~/venvs/pytest-kernel/bin/python -m pytest fixtures/samples/v2.1/pytest -q`
- **判**：dryrun 输出**无 `[ ERROR ]`**（退出码恒 0，不可信）；五态各 ≥1；metadata 六必填齐
- **证**：`docs/evidence/v2.1/SMPL-01/`（dryrun 原文 + `output.xml` + 五态计数）

### 10.2 ORCH-01 · 计划组实体与批量建边

- **改**：`db/migration/V9_1__plan_group.sql` + `db/down/`；新增 `domain/plan/PlanGroup.java`、`application/plan/PlanGroupService.java`、`application/repository/PlanGroupRepository.java`、`infrastructure/PlanGroupRepositoryJdbc.java`、`api/plan/PlanGroupController.java`；改 `application/plan/PlanService.java`、`frontend/src/api/plans.ts`、`frontend/src/views/PlansView.vue`
- **做**：① 两表 + down；② 组 CRUD 与成员排序；③ 批量建边**复用 `PlanDependencyGraph`**（成环抛 `PlanCycleException`，错误体带环上计划名）；④ 接口；⑤ 前端组面板
- **跑**：`bash scripts/db-migrate.sh` → `mvn -o -B test -Dtest=PlanGroupServiceTest` → `cd frontend && npx vitest run src/views/PlansView.spec.ts`
- **判**：3 计划成组产生 2 条边；重复加入幂等；成环被拒且错误含计划名；跨项目 403
- **证**：`docs/evidence/v2.1/ORCH-01/`（正/负各 1 次接口输出 + 库内边数查询）

### 10.3 ORCH-02 · 依赖图视图与关键路径

- **改**：新增 `api/plan/PlanGraphController.java`、`application/plan/PlanGraphService.java`；新增前端 `components/plan/PlanGraph.vue` + spec；改 `Views/PlansView.vue`
- **做**：① 接口返回 nodes/edges；② 关键路径 = 沿边累加 `execution_plan.timeout_seconds` 的最长路径；③ 前端 SVG 只读渲染，关键路径高亮、孤立点区分，标注「估算」；④ 复用 `capture.mjs` 取图
- **跑**：`npx vitest run src/components/plan/PlanGraph.spec.ts`；`node docs/evidence/v1.0/w7-design/capture.mjs <url>`
- **判**：50 节点首屏 ≤ 1s；关键路径与手算一致；孤立点视觉可辨
- **证**：`docs/evidence/v2.1/ORCH-02/`（计时读数 + 截图 + 手算对照表）

### 10.4 ORCH-03 · 失败聚类与重跑清单

- **改**：新增 `application/execution/FailureClusterService.java`（**构造注入既有 `FailureClassifier`**）、`api/execution/FailureClusterController.java`；新增前端 `views/FailureClustersView.vue` + spec；改 `frontend/src/api/runs.ts`
- **做**：① 取一轮/一组内的失败行；② 一级 `classify()`；③ 二级归一化簇键（§5.1）；④ **稳定排序**：类 → 条数倒序 → 簇键字典序（禁用 HashMap 迭代序）；⑤ 重跑清单复用 `CaseRunService`
- **跑**：`mvn -o -B test -Dtest=FailureClusterServiceTest`；性能用 `-Dtest=FailureClusterPerfTest`（1 万条合成数据）
- **判**：两次划分导出 JSON **逐字节一致**；1 万条 ≤ 3s；空字段入 `未分类`
- **证**：`docs/evidence/v2.1/ORCH-03/`（两次导出 JSON + `diff` 空 + 计时）

### 10.5 TPLX-01 · 模板实体与字段白名单

- **改**：`db/migration/V9_2__report_template.sql` + `db/down/`；**把 `ReportViewRenderer` 里的 C5 白名单抽成 `application/report/ReportFieldWhitelist.java`**（内容逐项照 `docs/plan/C5-对外报告字段白名单-20261106.md` §2，只搬家不改项）；新增 `domain/report/ReportTemplate.java`、`application/report/ReportTemplateService.java`、`api/report/ReportTemplateController.java`、`infrastructure/ReportTemplateRepositoryJdbc.java`；新增前端 `views/ReportTemplatesView.vue` + spec
- **做**：① 抽白名单并让 `ReportViewRenderer` 改为引用它（行为不变）；② 两表；③ 提交的 `field_key` 必须命中白名单，否则 400 并列出非法键；④ 平台级/项目级；⑤ 前端勾选 + 拖排 + 改名
- **跑**：`mvn -o -B test -Dtest=ReportTemplateServiceTest`；`npx vitest run src/views/ReportTemplatesView.spec.ts`；回归 `mvn -o -B test -Dtest=ReportViewRendererTest`
- **判**：白名单外字段双拦截（接口 400 + 前端不可选）；抽常量后 EXTERNAL 渲染结果**与抽前逐字段一致**
- **证**：`docs/evidence/v2.1/TPLX-01/`（负向 2 条 + 抽前/抽后渲染 diff 空）

### 10.6 TPLX-02 · 按模板冻结 + xlsx 导出

- **改**：`application/report/ReportSnapshotService.java`（快照记 `template_id` / `template_version`）、`ReportViewRenderer.java`（按模板裁剪）、`ReportExportService.java`（**加 xlsx 分支**，POI 已在 DOCX 在用）、`api/report/ReportExportController.java`（`format` 加 xlsx）
- **做**：① 冻结时把模板 ID 与版本写进快照，渲染**读快照内模板**而非当前模板；② xlsx 用 `XSSFWorkbook`，通过率等写数值型（`setCellValue(double)`）；③ 未指定模板回落 DEFAULT
- **跑**：`mvn -o -B test -Dtest=ReportExportServiceTest`；端到端「冻结 → 改模板 → 重开报告」；`python3 -c "import openpyxl;..."` 读单元格类型
- **判**：改模板不影响已冻结报告；xlsx 数值列 `cell.data_type == 'n'`；四格式内容一致
- **证**：`docs/evidence/v2.1/TPLX-02/`（xlsx 文件 + openpyxl 读数 + 改模板前后渲染 diff）

### 10.7 TPLX-03 · 四格式一致性校验

- **改**：新增 `tools/check_report_consistency.py`；B4 收敛后往 `scripts/gates.sh` 加一步（第 14 步位置）
- **做**：① 解析 HTML / DOCX（POI 或 python-docx）/ XLSX（openpyxl）/ PDF（ASCII 文本层）的字段表头；② 比对字段集合；③ 不一致时列出缺失字段并退出 1
- **跑**：`python3 tools/check_report_consistency.py <html> <docx> <xlsx> <pdf>`
- **判**：一致 → 0；人为从 xlsx 删一列 → 必须非 0 并指出缺哪列（负向自检）
- **证**：`docs/evidence/v2.1/TPLX-03/`（正/负各 1 次输出）

### 10.8 PUBX-01 · 版本标识与引用方检测

- **改**：`db/migration/V9_3__case_source_version.sql` + `db/down/`；新增 `domain/caseasset/AssetVersion.java`、`application/repository/AssetVersionRepository.java`、`infrastructure/AssetVersionRepositoryJdbc.java`、`application/caseasset/AssetVersionService.java`、`api/caseasset/AssetVersionController.java`；改 `application/caseasset/TestSetService.java`（add/remove/reorder 接版本钩子）、`infrastructure/TestSetRepositoryJdbc.java`（reorder 撞唯一键修复，见下）、`scripts/db-audit.sh`（64→65）、`application/iam/AuditAspectTest.java`（构造签名）
  - 修订 2026-11-06：计划原写「改 ProjectAssetRepositoryJdbc / AssetScopeSql」，实际引用方检测走**新表 asset_version**（`source_asset_id` 反查），不动既有 `project_asset` 引用链——两套语义（资产引用 vs 内容副本）分开，避免把「引用了资产」误当成「做了副本」。
- **做**：① 平台资产算 `content_hash`（用例集内容规范化后哈希）+ 维护 `asset_version`；② 项目副本存 `synced_asset_version` / `fork_state`；③ 引用方列表带「有新版本」标记（hash 或版本任一变化才置位）
- **跑**：`bash scripts/db-migrate.sh` → `mvn -o -B test -Dtest=AssetVersionServiceTest,AuditAspectTest` → `python3 docs/evidence/v2.1/PUBX-01/run.py`
- **判**：内容不变不出新版本（不误报）；变更后引用方可见提示；只改顺序按「算变更」处理并在文档写明
- **证**：`docs/evidence/v2.1/PUBX-01/`（真实 HTTP 13 项全 PASS，含三种变更的版本读数与 reorder 修复证据）
- **顺带修复（存量 bug）**：`TestSetRepositoryJdbc.reorder` 两元素换序撞 `uk_test_set_item(test_set_id, seq_no)`（真实链路 500），改三段式（平移腾位 → 落新序 → 余量接尾），由本包验收暴露并留端到端证据

### 10.9 PUBX-02 · 差异对比与同步/分叉

- **改**：`application/caseasset/AssetVersionService.java`（diff/sync/diverge）、`application/repository/AssetVersionRepository.java` + Jdbc（findCaseIdByCode / insertCase / updateCaseFields / replaceItems）、`api/caseasset/AssetVersionController.java`（/diff /sync /diverge）；改前端 `views/CaseSetsView.vue` + spec、`api/cases.ts`（assetVersionApi）、双语言包
- **做**：① 差异分新增/修改/删除三类（按 case_code 匹配，顺序差异归 MODIFIED）；② 同步 = 缺失用例导入 + 复用行字段对齐 + 条目按平台顺序整体重建 + synced 读数推进；③ 保持分叉 = 只置 `fork_state=DIVERGED`，内容与 synced 读数原样保留；④ 两个动作都写审计（ASSET_SYNC / ASSET_DIVERGE）
- **跑**：`mvn -o -B test -Dtest=AssetUpgradeServiceTest,AssetVersionServiceTest`；`npx vitest run src/views/CaseSetsView.spec.ts`；`python3 docs/evidence/v2.1/PUBX-02/run.py`
- **判**：三类差异条数正确；同步后版本对齐；分叉后副本内容不变且列表可辨
- **证**：`docs/evidence/v2.1/PUBX-02/`（真实 HTTP 15 项全 PASS + `out/audit.log` 审计查询结果）
- 修订 2026-11-06：计划原写改 `AssetsView.vue`，实际落 `CaseSetsView.vue`（版本是用例集的属性，跟着成员面板走）；验收暴露两处实现修正——replaceItems 的 INSERT 补租户条件、同步补 updateCaseFields（复用行不对齐字段则 hash 永远差一版），详见证据 README「踩过的坑」

### 10.10 NODX-01 · 节点注册与心跳

- **改**：`db/migration/V9_4__execution_node.sql` + `db/down/`（顺带加 `run.node_id` 列，NODX-02 不再单开迁移）；新增 `domain/node/ExecutionNode.java`、`application/repository/ExecutionNodeRepository.java` + Jdbc、`application/node/ExecutionNodeService.java`、`application/node/NodeHeartbeatMonitor.java`、`api/node/NodeController.java`；改 `api/WebConfig.java`（心跳端点加拦截器白名单）、`scripts/db-audit.sh`（65→66）；新增前端 `views/NodesView.vue` + spec、`api/nodes.ts`、`router/index.ts`（/nodes）、双语言包、`SettingsView.vue`（入口按钮）
- **做**：① 注册接口签发令牌，**只存哈希**、明文仅返回一次；② 心跳接口（X-Node-Token 头自认证，拦截器白名单放行）更新 `last_heartbeat_at`；③ 定时器连续 3 次缺心跳置 `OFFLINE`；④ 能力上报（RF / pytest 可用性）；⑤ 四态 NEW/ONLINE/OFFLINE/DISABLED（停用心跳 403，启用回 NEW）
- **跑**：`bash scripts/db-migrate.sh` → `mvn -o -B test -Dtest=ExecutionNodeServiceTest,NodeHeartbeatMonitorTest` → `npx vitest run src/views/NodesView.spec.ts` → `python3 docs/evidence/v2.1/NODX-01/run.py`
- **判**：3 次缺心跳转 `OFFLINE`；令牌不入日志、不入库明文；节点列表显示能力与并发占用
- **证**：`docs/evidence/v2.1/NODX-01/`（真实 HTTP 10 项全 PASS：16MB 日志无明文令牌、库内哈希逐字节比对、Monitor 真实扫描置离线；`out/log-sample.txt` 抽样）
- 修订 2026-11-06：并发占用（runningRuns）显示推迟到 NODX-02 接上 run.node_id 之后（本迁移只加列，没有可计数的运行归属），判据的「占用」部分随 NODX-02 补齐

### 10.11 NODX-02 · 指定节点执行与离线拒绝

- **改**：`application/execution/RunDispatcher.java`、`infrastructure/ProjectRunDispatcher.java`（**只加分支，不改本机路径语义**）、新增 `application/node/NodeChannel.java`、改 `api/execution/` 发起接口加 `nodeId` 参数
- **做**：① 发起时可选 `nodeId`，写 `run.node_id`；② 节点非 `ONLINE` → 直接拒绝并给原因，**不回落本机**；③ 节点并发按节点独立计数
- **跑**：`mvn -o -B test -Dtest=RunDispatchNodeTest`（正/负各 1 条）
- **判**：离线节点请求被拒且原因明确；未指定走本机（本机路径回归全绿）；并发独立
- **证**：`docs/evidence/v2.1/NODX-02/`（真实 HTTP 12 项全 PASS：NEW/OFFLINE/DISABLED 三类拒绝各带原因文案与「不回落本机」、拒绝发生在落库前、`run.node_id` 真实写库过租户守卫、`RUN_CREATE` 审计含 `node=`、未指定走本机 `node_id IS NULL`；`RunDispatchNodeTest` 6 条 + `NodeChannelTest` 4 条全绿——并发独立用真实 `ProjectRunDispatcher` 本机道占满时节点道照常并行验证）
- **实测结论（2026-11-06/07，完成回写）**：
  - 节点执行体（`RunExecutionService.executeOnNode`）本版为**诚实占位**：指定节点的 run 派发进节点道后判 FAILED（原因码 `node_channel_unavailable`，文案「不回落本机执行」）——节点接入地址随 NODX-03 迁移才登记，本版绝不静默回落本机 pabot；NODX-03 把该主体换成 NodeChannel 真实投递。
  - §10.10 推迟项补齐：`GET /api/nodes` 每行带 `runningRuns`（按 `run.node_id` 聚合 `run_status='running'`），前端节点页加「在跑任务」列。
  - 修订记录：① `RunService` 加节点仓储后 `@Autowired` 必须放全参主构造——留在便捷构造上生产装配拿到 NO_NODES 桩，症状是「刚注册的节点发起报不存在」，单测（手写构造）抓不到，HTTP 验收第一轮抓到；② `CreateRunCommand` 用旧 12 参构造委托新 13 参，8 处既有调用点零改动；③ `ProjectRunDispatcher` 内部道键从裸项目 id 换 `p<id>`/`n<nodeId>` 字符串，本机语义等价不变；④ `NodeChannel` 出站纪律（超时 10s / 网络失败重试 1 次 / HTTP 拒绝不重试）由 `NodeChannelTest` 锁死。

### 10.12 NODX-03 · 执行端 agent 与命令等价

- **改**：新增 `api/node/NodeAgentController.java`（接任务）、`application/node/NodeAgentRunner.java`（调 `RunExecutionService`，**命令仍由 `PabotCommandBuilder` / KRN 生成**）；新增 `application/node/NodeResultUploader.java`（产物与 `output.xml` 回传）；新增 `config/application-node.yml`（`--spring.profiles.active=node`）
- **做**：① 同 jar 双角色（主控 / 节点）；② 任务签名校验；③ 产物回传后主控解析入库；④ agent 崩溃时主控按心跳超时判失败
- **跑**：`mvn -o -B test -Dtest=NodeAgentRunnerTest`；端到端：本机 `:8082` 起节点跑 1 条示例用例；`python3 tools/kernel_cmd_diff.py`（命令 diff）
- **判**：同参数命令 **diff 为空**；产物完整回传；节点崩溃不留悬挂 run
- **证**：`docs/evidence/v2.1/NODX-03/`（命令 diff 空 + 产物清单 + 崩溃注入记录）
- **实测结论（2026-11-07，完成回写）**：端到端 14 项全 PASS（真实 HTTP + 真实节点进程 `--spring.profiles.active=node` :8083 + 真实 RF 执行 + mysql 直查取证）。核心判据逐条实证：① 同参数命令 diff 为空（`kernel_cmd_diff.py` 归一化绝对路径后 8 参数逐项一致——节点不自己拼命令，复用主控 KRN 同参数 build）；② 产物完整回传（output.xml 经 `/api/agent/result` 落主控产物目录并解析入 test_result）；③ 崩溃不留悬挂 run（killpg 杀节点进程 → 心跳停止经 last_heartbeat_at 不变实证 → `NodeHeartbeatMonitor` 判 FAILED reason=`node_agent_lost` 并置 OFFLINE，不排队不重投不回落本机）。
  - 实现与计划差异：计划中的 `NodeResultUploader` 落地为 `NodeChannel`（出站 HttpClient，超时 10s/网络失败重试 1 次/HTTP 拒绝不重试）+ `NodeResultService`/`NodeResultController`（回传接收入口 `POST /api/agent/result`，X-Node-Token 认证 + run.node_id 归属核对防替跑）；签名 key 为 `sha256Hex(token)`，主控存哈希可自算、节点持明文派生。
  - 修订记录：① V9_5 迁移加 `execution_node.agent_base_url`（计划未列，注册时登记 agent 接入地址）；② 主控 HttpClient 复用 stale keep-alive 连接导致投递报 `header parser received no bytes`——网络失败重试时新建 HttpClient；③ 主控与节点对同一 run 双重 PREPARING 迁移撞状态机——`execute` 开头幂等预查（state != PREPARING 才迁移）；④ EXEC-05 口径实证：robot_file 是脚本真源，materialize 只落库内脚本，端到端 seed 必须直插 robot_file；⑤ 单测 24 条节点相关全绿（`NodeAgentRunnerTest` 4 条 + 既有桩适配）。

### 10.13 NODX-04 · 节点页与离线口径

- **改**：`views/NodesView.vue`（四态 + 离线时长 + 最近任务）、`docs/CONVENTIONS.md` 或本文件 §5.3 补口径
- **做**：① 页面四态；② 离线时长计算与排序；③ 口径写入文档（本版：**拒绝发起，不排队**）
- **跑**：`npx vitest run src/views/NodesView.spec.ts`；`node docs/evidence/v1.0/w7-design/capture.mjs <url>`
- **判**：四态齐全；离线时长正确；口径在文档里可检索到并被 TEST-PLAN 引用
- **证**：`docs/evidence/v2.1/NODX-04/`（4 张截图）
- **实测结论（2026-11-07，完成回写）**：HTTP 层 8 项全 PASS（`/api/nodes` 每行带
  `agentBaseUrl`/`lastRun`；注册表单的 agent 接入地址真实落库；NEW 节点发起 409
  当场拒绝且 run 未落库——不排队不重投不回落本机；NEW 节点 `lastHeartbeatAt=null`
  前端只显占位不伪造离线）；浏览器层 4 张截图 + 4 项检查全 PASS（1440 列表真实渲染
  最近任务 `RUN-… · 失败` 与心跳相对时长 `1 小时前`、注册表单含 agent 地址输入与
  提示、令牌一次性弹窗 64 hex、1280 宽 scrollWidth=1280 无横滚）；`NodesView.spec.ts`
  7 条全绿；后端 882 条测试全绿。
  - 离线口径已写入本文件 §5.3（非 ONLINE 一律当场拒绝——不排队、不重投、不回落本机
    执行；投递后失联由监视器判 `node_agent_lost`），TEST-PLAN TC-v2.1-25/27 判据同步引用。
  - 实现与计划差异：计划只列前端与文档，实际为「最近任务」加了平台级读数
    `latestRunsByNode()`（`ExecutionNodeRepository` + Jdbc，豁免口径与
    runningRunsByNode 同注）与 `NodeView` 扩字段——前端只做展示，不发明数据。
  - 修订记录：① `:8001` 服务构建产物，改完 NodesView 必须 `npm run build` 再截图，
    否则取证拍的是旧行为（第一轮 S1/S2 因此 FAIL）；② NInputNumber 也渲染
    `<input>`，spec 按 placeholder 定位输入框；③ 运行状态词条在 `enum.runStatus`
    段，组件 `te()` 探测后回退原始 state；④ 截图脚本注册名带时间戳后缀，可重复执行。

---

## 修订记录

| 日期 | 版本 | 改了什么 | 为什么改 |
|---|---|---|---|
| 2026-11-06 | v1.0 | 初版：三层结构 + 4 个迁移 + 4 波（W8–W11）+ 13 包 + 聚类簇键口径 + 模板安全边界 + 节点通道 + 文件冲突规避 + 9 条门禁 | 承接 PRD v1.0 的 17 条 FR，按「先底座、让开 A8、C 类最后」排波次 |
| 2026-11-06 | v1.1 | ① 新增 **§10 逐包开工单**：13 包各给「改 / 做 / 跑 / 判 / 证」五段，文件路径逐条核对到类级；② §5.1 聚类改为**类 + 簇两级**（复用 `FailureClassifier`，不重写分类）；③ §4.2 ORCH-03 同步；④ 明确证据根改为 `docs/evidence/v2.1/`，v2.0 旧证据不搬迁 | 大师要求计划落到可执行粒度；写开工单时读代码发现分类器已存在，避免重复造组件 |
| 2026-11-06 | v1.2 | §5.1 簇键来源订正为**只取 `test_result.failure_msg` 首行**，并注明 `run` 表三字段是整轮结论 | ORCH-03 实测：`test_result` 无 `failure_reason` / `failure_message` 列，原文照字面实现会读不到值 |

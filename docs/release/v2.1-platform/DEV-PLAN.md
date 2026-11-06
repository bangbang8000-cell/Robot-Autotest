# v2.1 提效与扩展 · ④ 开发计划

**文档编号** RA-REL-2026-021-D ｜ **版本** v2.1-platform ｜ **日期** 2026-11-06 ｜ **状态** 待评审（候选计划，未开工）
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
| ORCH-03 | 失败聚类 + 建议重跑清单（簇键：`failure_reason` 优先，缺失取 `failure_message` 归一化首行） | ORCH-01 | 1 万条输入 ≤ 3s；同一输入两次划分一致；清单可直接发起 run | 基准脚本 + 两次结果 diff |

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

| 顺序 | 取值来源 | 归一化规则 |
|---|---|---|
| 1 | `test_result` 有 `failure_reason` | 直接作为簇键，trim 后取前 40 字符 |
| 2 | 否则取 `failure_message` 首行 | 去路径（`/…/x.py` → `<path>`）、去数字（`\d+` → `<n>`）、去 IP、去端口、trim 后前 40 字符 |
| 3 | 都为空 | 归入固定簇 `未分类` |

- **不得随机**：不做 k-means、不做 embedding，纯确定性规则（`FR-v2.1-04` 要求两次结果一致）。
- 簇名展示「簇键 + 条数 + 占比」，不生成自然语言摘要（那是 AI 的事，本版不做）。

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
- 出站一律过 `NodeChannel`（统一超时 10s、重试 1 次、失败即判节点离线，不重试到底）。
- 本机执行路径**不经过** `NodeChannel`，保持 v2.0 语义不变（避免为了统一而引入回归）。

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

- [ ] 12 步机器门 `FAILS=0`
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

## 修订记录

| 日期 | 版本 | 改了什么 | 为什么改 |
|---|---|---|---|
| 2026-11-06 | v1.0 | 初版：三层结构 + 4 个迁移 + 4 波（W8–W11）+ 13 包 + 聚类簇键口径 + 模板安全边界 + 节点通道 + 文件冲突规避 + 9 条门禁 | 承接 PRD v1.0 的 17 条 FR，按「先底座、让开 A8、C 类最后」排波次 |

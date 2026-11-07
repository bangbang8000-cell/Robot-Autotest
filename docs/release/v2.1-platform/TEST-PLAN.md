# v2.1 提效与扩展 · ⑤ 测试计划

**文档编号** RA-REL-2026-021-T ｜ **版本** v2.1-platform ｜ **日期** 2026-11-06 ｜ **状态** 待评审（未开工，0 条 TC 执行）
**输入** `PRD.md`（17 条 FR / 8 条 NFR / 9 条验收）、`DEV-PLAN.md`（4 波 / 13 包）
**原则** 每条 FR 至少 1 条 TC；每个 TC 必须能回答「跑了什么、原始输出是什么、凭哪个数判定」

> **与 v2.0 的关系**：本计划**不重测** v2.0 的 67 条 TC（在 `v2.0-platform/05-TEST-REPORT.md`）。
> v2.0 的 42 条 PARTIAL 由 `v2.0-platform/06-收口执行计划-20261106.md` 负责，两条线互不抵扣。

---

## 1. 策略与分层

**层代码图例**

| 代码 | 含义 | 判定依据 |
|---|---|---|
| **G** | 真实链路（真库 / 真进程 / 真 Chrome / 真节点） | 命令输出 + 落库数字 |
| **I** | 接口 / 集成 | HTTP 状态 + 响应体断言 |
| **E** | 端到端 / 浏览器 | 截图 + DOM 量测 |
| **U** | 单测 / 组件 | 测试报告条数与断言 |
| **S** | 安全负向 | 拒绝结果 + 日志抽样 |
| **P** | 性能 | 计时读数（可复跑脚本） |

**占比目标**：U 40% / I 25% / G 25% / E+S+P 10%。

**测试数据**：一律用 SMPL-01 的 5–10 条**不映射业务**的示例用例（拍板第 1 条 + FR-v2.1-17）；
聚类用 `test_result` 历史数据 + 合成 1 万条；节点用本机第二端口模拟（真机验收归入现场日，拍板第 4 条）。

---

## 2. 回归基线（不得倒退）

| 项 | 基线 | 判定 |
|---|---|---|
| 后端单测 | 780 条，0 失败 | `mvn -o -B test` 全绿且条数不减 |
| 前端单测 | 239 条 | 全绿且条数不减 |
| editor 单测 | 219 条 | 全绿且条数不减（独立 vitest，`frontend` 扫不到） |
| 库结构 | `V8_6` / 60 表 / 30 迁移 + 本版 V9_1–V9_4 | `db-audit` 7 项 PASS |
| 既有 RF 命令 | 命令 diff 为空 | `tools/kernel_cmd_diff.py` 输出 0 行 diff |
| v2.0 已冻结报告 | 渲染结果不变 | 历史快照 hash 回归 |
| 机器门 | 16 步 `FAILS=0` | `scripts/gates.sh` |

---

## 3. 波次出口

| 波次 | 出口必测 | 出口判据 |
|---|---|---|
| W8 | TC-01 ~ TC-09、TC-23、TC-24 | 组内顺序与自动建边正确；同输入两次聚类结果字节级一致；五态示例用例可跑 |
| W9 | TC-10 ~ TC-14、TC-26 | xlsx 可打开且数值是数字；四格式 EXTERNAL 扫描 0 命中；改模板不影响已冻结报告 |
| W10 | TC-15 ~ TC-17 | 内容未变不误报；三类差异正确；同步/分叉两态可辨 |
| W11 | TC-18 ~ TC-22 | 注册→心跳→指定执行→回收端到端 1 条；离线拒绝 1 条；命令 diff 为空 |
| 全版 | TC-25 + §5 专项 | 三页四态齐全；1440 利用率 ≥ 78%；1280 无横滚 |

---

## 4. 用例清单

### 4.1 计划编排（FR-v2.1-01 ~ 03）

| TC | 层 | 覆盖 | 场景 | 期望结果 |
|---|---|---|---|---|
| TC-v2.1-01 | G | FR-v2.1-01 | 建组 → 加 3 个计划 → 停用组 → 观察调度 | 组内 3 计划成组成功；停用后组内计划不再触发；计划本身仍在计划列表可单独启用 |
| TC-v2.1-02 | I | FR-v2.1-01 | 调整组内顺序后重新查询；用另一项目账号访问该组 | 顺序持久化（重查一致）；跨项目访问 403 且不泄露组名 |
| TC-v2.1-03 | G | FR-v2.1-01 | 删除组 | 组被删，组内计划保留；依赖边按口径清理（文档写明：边随组删，计划保留） |
| TC-v2.1-04 | I | FR-v2.1-02 | 3 个计划批量成组 | 自动产生 2 条边，方向为组内顺序；边数与顺序均可查 |
| TC-v2.1-05 | I | FR-v2.1-02 | 同一计划重复加入同一组 | 幂等：不产生重复边；返回既有成员记录 |
| TC-v2.1-06 | I | FR-v2.1-02 | 构造成环（A→B→C→A） | 拒绝保存；错误信息列出环上的计划名；库内无新增边 |
| TC-v2.1-07 | E | FR-v2.1-03 | 打开 10 计划组的依赖图 | 节点/边与库内一致；关键路径高亮；孤立点有区分；预计时长标注「估算」 |
| TC-v2.1-08 | P | FR-v2.1-03 | 50 节点依赖图渲染计时 | 首屏 ≤ 1s；脚本可复跑并留读数 |

### 4.2 失败聚类（FR-v2.1-04 ~ 05）

| TC | 层 | 覆盖 | 场景 | 期望结果 |
|---|---|---|---|---|
| TC-v2.1-09 | G | FR-v2.1-04 | 同一批 1 万个 `test_result` 跑两次聚类 | 两次划分**完全一致**：一级类集合、二级簇键集合、每簇条数、簇内用例 ID 列表逐一相等 |
| TC-v2.1-10 | P | FR-v2.1-04 | 1 万条输入聚类计时 | ≤ 3s，留计时读数 |
| TC-v2.1-11 | U | FR-v2.1-04 | 归一化规则固定样本（含路径、数字、IP、端口）；构造四类各 1 条 | `/a/b/x.py:12` 与 `/c/d/y.py:98` 归一为同簇；`AssertionError: 3 != 5` 与 `3 != 7` 同簇（数字归一）；四类分别落到 CANCELED / ENV / INFRA / ASSERT（**复用既有 `FailureClassifier`，不新写判定**） |
| TC-v2.1-12 | I | FR-v2.1-04 | 无 `failure_reason` 且 `failure_message` 为空的结果 | 二级簇入固定 `未分类`，一级类仍由分类器给出，不丢数据 |
| TC-v2.1-13 | E | FR-v2.1-05 | 从最大簇生成重跑清单并发起 run | 清单条数 = 簇内可重跑条数；run 发起成功；动作落审计 |

### 4.3 报告模板与导出（FR-v2.1-06 ~ 09）

| TC | 层 | 覆盖 | 场景 | 期望结果 |
|---|---|---|---|---|
| TC-v2.1-14 | S | FR-v2.1-06 | 接口提交白名单外字段键；前端尝试选中 | 接口 400 并列出非法键；前端该字段不可选（两条负向都过） |
| TC-v2.1-15 | I | FR-v2.1-06 | 调整字段顺序、覆盖标题文案、设为 EXTERNAL | 重查一致；平台级模板被 2 个项目同时引用成功 |
| TC-v2.1-16 | G | FR-v2.1-07 | 用模板 A 冻结报告 → 改模板 A 的字段 → 重新打开报告 | 已冻结报告渲染不变；快照内记录模板 ID 与版本；新建报告用改后模板 |
| TC-v2.1-17 | G | FR-v2.1-08 | 导出 xlsx 并用 WPS/Excel 打开 | 可打开无水印冲突；表头与模板一致；通过率为数字类型（非文本） |
| TC-v2.1-18 | S | FR-v2.1-08 | EXTERNAL 模板导出 HTML / PDF / DOCX / XLSX 各 1 份 | `check_report_sensitive.py` 四份全部 0 命中（含 IP、命令、凭据） |
| TC-v2.1-19 | I | FR-v2.1-09 | 构造四格式字段集合不一致 | 导出报错并列出缺失字段；一致时四份字段集合相等 |

### 4.4 公用资产升级（FR-v2.1-10 ~ 12）

| TC | 层 | 覆盖 | 场景 | 期望结果 |
|---|---|---|---|---|
| TC-v2.1-20 | U | FR-v2.1-10 | 平台用例集：不改变更 / 改变更 / 只改顺序 | 不改不出新版本；改变更出新版本；只改顺序按口径处理（写明：算变更） |
| TC-v2.1-21 | E | FR-v2.1-11 | 打开引用方列表并查看差异 | 显示「有新版本」；差异分新增/修改/删除三类且条数正确；无变更不显示提示 |
| TC-v2.1-22 | G | FR-v2.1-12 | 项目 A 同步、项目 B 保持分叉 | A 版本对齐；B 内容不动且标记「已分叉」；两条动作均可在审计查到 |

### 4.5 第二执行节点（FR-v2.1-13 ~ 16）

| TC | 层 | 覆盖 | 场景 | 期望结果 |
|---|---|---|---|---|
| TC-v2.1-23 | G | FR-v2.1-13 | 注册节点（本机第二端口）→ 正常心跳 → 停心跳 3 个周期 | 状态 `ONLINE` → `OFFLINE`；节点列表显示能力与并发占用 |
| TC-v2.1-24 | S | NFR-v2.1-04 | 未带令牌调节点接口；查日志与库 | 401 且不泄露节点存在性；库内只有哈希；日志无明文令牌 |
| TC-v2.1-25 | I | FR-v2.1-14 | 对 `OFFLINE` 节点发起 run；不指定节点发起 run | 离线节点被拒并提示原因；**不排队、不重投、不回落本机**（DEV-PLAN §5.3 离线口径）；不指定走本机；节点并发独立计数 |
| TC-v2.1-26 | G | FR-v2.1-15 | 同参数分别在本机与节点执行 | 命令 diff **为空**；`output.xml` 与产物完整回传；主控可解析 |
| TC-v2.1-27 | E | FR-v2.1-16 | 打开节点页 | 四态状态原样显示（前端不推断）；离线时长（相对时间，无心跳只显占位不伪造离线）；最近任务（runId + 本地化状态）；agent 接入地址随注册表单可填（NODX-04）；四态（加载/空/错/有数据）齐全 |

### 4.6 示例用例集（FR-v2.1-17）

| TC | 层 | 覆盖 | 场景 | 期望结果 |
|---|---|---|---|---|
| TC-v2.1-28 | G | FR-v2.1-17 | RF 示例套件 `--dryrun` + 实跑 | dryrun 输出**无 `[ ERROR ]`**（退出码不可信，必须双判）；实跑覆盖 pass / fail / skip 三态 |
| TC-v2.1-29 | G | FR-v2.1-17 | pytest 示例套件实跑（随 A1 的 venv） | 覆盖 pass / fail / xfail / error 四态；`--json-report` 产物可解析 |
| TC-v2.1-30 | I | FR-v2.1-17 | 检查每套件 metadata | 六必填齐（`CASE_ID`/`RUN_ID`/`QA_PROJECT`/`DUT`/`LINK`/`SW_BUILD`）；值 ≤ 40 字符；无凭据 |

### 4.7 界面与迁移

| TC | 层 | 覆盖 | 场景 | 期望结果 |
|---|---|---|---|---|
| TC-v2.1-31 | E | PRD §6 界面条 | 模板配置页 / 节点页 / 聚类页三页 × 1440 与 1280 | 四态齐全；1440 利用率 ≥ 78%；1280 无横滚（`scrollWidth` 读数） |
| TC-v2.1-32 | G | NFR-v2.1-08 | V9_1–V9_4 逐个 down 再 up；备份恢复 | down 后表/列消失且不影响 V8_x 数据；重新 up 成功；备份恢复后行数一致 |

> **实测（2026-11-07）：TC-31 PASS。** `scripts/v21/ui-three-pages.mjs` 在真实 Chrome（CDP）里
> 跑三页 × 四态 × 双视口，36 项断言全 PASS；1440 内容区利用率 12/12 组均为 **86.1%**（≥78%），
> 1280 全部 `scrollWidth=1280` 无横滚；截图 24 张。证据：`docs/evidence/v2.1/TC31-UI/README.md`。

### 4.8 执行器与 runner 映射（v1.1 新增）

> 目的：32 条 TC 每条都要能落到「哪个脚本 / 哪个测试类 + 一条命令 + 一个证据文件」，
> 否则「跑过」只是一句话。新增脚本统一放 `scripts/v21/`（本版新建），命名 `<主题>-<动作>.sh|mjs`。

### 五个执行通道

| 通道 | 用在哪层 | 命令模板 | 产物 |
|---|---|---|---|
| **BE** | U / I（后端） | `cd backend && mvn -o -B test -Dtest=<类名>` | surefire 摘要（条数 + 失败数） |
| **FE** | U（前端） | `cd frontend && npx vitest run <路径>` | vitest 输出 |
| **UI** | E | `node scripts/v21/ui-<主题>.mjs`（内部调 `docs/evidence/v1.0/w7-design/capture.mjs`） | 截图 + DOM 量测 JSON |
| **CHAIN** | G | `bash scripts/v21/<主题>-<动作>.sh`（真起 `:8001`、真库、真节点 `:8082`） | 脚本 stdout + 落库查询结果 |
| **NEG** | S | `bash scripts/v21/<主题>-<动作>.sh --negative` | 拒绝响应 + 日志抽样 |

### 逐条映射

| TC | 通道 | 执行器 | 证据文件名 |
|---|---|---|---|
| TC-v2.1-01 | CHAIN | `scripts/v21/plan-group-e2e.sh` | `01-group-lifecycle.log` |
| TC-v2.1-02 | NEG | `scripts/v21/plan-group-authz.sh --negative` | `02-cross-project-403.log` |
| TC-v2.1-03 | BE | `PlanGroupServiceTest#deletesGroupKeepsPlans` | `03-delete-group.txt` |
| TC-v2.1-04 | BE | `PlanGroupServiceTest#autoBuildsEdges` | `04-edges-created.txt` |
| TC-v2.1-05 | BE | `PlanGroupServiceTest#idempotentAdd` | `05-idempotent.txt` |
| TC-v2.1-06 | BE | `PlanGroupServiceTest#rejectsCycle` | `06-cycle-rejected.txt` |
| TC-v2.1-07 | UI | `scripts/v21/ui-plan-graph.mjs` | `07-graph.png` + `07-graph.json` |
| TC-v2.1-08 | CHAIN | `scripts/v21/plan-graph-perf.sh` | `08-graph-perf.log` |
| TC-v2.1-09 | CHAIN | `scripts/v21/cluster-determinism.sh` | `09-cluster-a.json` / `09-cluster-b.json` / `09-diff.log` |
| TC-v2.1-10 | CHAIN | `scripts/v21/cluster-perf.sh` | `10-cluster-perf.log` |
| TC-v2.1-11 | BE | `FailureClusterServiceTest#normalizationAndClass` | `11-normalize.txt` |
| TC-v2.1-12 | BE | `FailureClusterServiceTest#uncategorizedFallback` | `12-uncategorized.txt` |
| TC-v2.1-13 | CHAIN | `scripts/v21/cluster-rerun.sh` | `13-rerun.log` |
| TC-v2.1-14 | NEG | `scripts/v21/template-whitelist.sh --negative` | `14-whitelist-reject.log` |
| TC-v2.1-15 | BE | `ReportTemplateServiceTest` | `15-template-crud.txt` |
| TC-v2.1-16 | CHAIN | `scripts/v21/report-freeze-template.sh` | `16-freeze-immutable.log` |
| TC-v2.1-17 | CHAIN | `scripts/v21/report-export-xlsx.sh` | `17-export.xlsx` + `17-celltypes.log` |
| TC-v2.1-18 | NEG | `scripts/v21/report-sensitive-4formats.sh --negative` | `18-sensitive-4f.log` |
| TC-v2.1-19 | CHAIN | `scripts/v21/report-consistency.sh` | `19-consistency.log` |
| TC-v2.1-20 | BE | `AssetVersionServiceTest#versionBump` | `20-version.txt` |
| TC-v2.1-21 | UI | `scripts/v21/ui-asset-diff.mjs` | `21-diff.png` |
| TC-v2.1-22 | CHAIN | `scripts/v21/asset-upgrade.sh` | `22-sync-fork.log` |
| TC-v2.1-23 | CHAIN | `scripts/v21/node-heartbeat.sh` | `23-heartbeat.log` |
| TC-v2.1-24 | NEG | `scripts/v21/node-auth.sh --negative` | `24-node-401.log` |
| TC-v2.1-25 | BE | `RunDispatchNodeTest#offlineRejected` | `25-offline-rejected.txt` |
| TC-v2.1-26 | CHAIN | `scripts/v21/node-cmd-diff.sh` | `26-cmddiff.log` |
| TC-v2.1-27 | UI | `scripts/v21/ui-nodes.mjs` | `27-nodes.png` |
| TC-v2.1-28 | CHAIN | `scripts/v21/sample-rf.sh` | `28-rf-dryrun.log` + `output.xml` |
| TC-v2.1-29 | CHAIN | `scripts/v21/sample-pytest.sh` | `29-pytest-json.json` |
| TC-v2.1-30 | CHAIN | `scripts/v21/sample-metadata.sh` | `30-metadata.log` |
| TC-v2.1-31 | UI | `scripts/v21/ui-three-pages.mjs` | `31-*.png` + `31-measure.json` |
| TC-v2.1-32 | CHAIN | `scripts/v21/migrate-roundtrip.sh` | `32-up-down-up.log` |

**约定**

1. 每个 `CHAIN` 脚本自己起服务、自己清理（跑完断言计数归零），**不依赖「先手动起服务」**。
2. `NEG` 脚本必须同时留「负向被拒」和「正向仍通」两段，只测负向会把功能测死。
3. 脚本未接入门禁前先手动跑，接入门禁是 B4 之后的第 13/14 步。
4. 一个脚本对应一个 TC，不合并——合并后失败定位不到条。

---

## 5. 专项测试

### 5.1 聚类确定性专项（TC-v2.1-09/11 的放大）

同一份 1 万条输入，**不同进程、不同机器、不同顺序**跑三次，簇划分必须逐字节一致（导出 JSON 后 diff）。
任何一次不一致即视为 FR-v2.1-04 未达成。

### 5.2 四格式一致性专项（TC-v2.1-18/19 的放大）

同一份 EXTERNAL 报告，四格式导出后：
① 字段集合比对相等；② 敏感扫描 0 命中；③ 通过率数值四份一致（不出现 HTML 是 92.3%、xlsx 是 92% 的偏差）。

### 5.3 节点故障注入（TC-v2.1-23/25 的放大）

| 注入 | 期望 |
|---|---|
| 节点进程被杀（run 执行中） | 主控在心跳超时后判 run 失败，错误文案含节点名；不留悬挂 run |
| 网络不通（`iptables` 丢包） | 同上；重试 1 次后不再重试 |
| 节点回传延迟 | 主控不阻塞本机执行（并发验证） |
| 节点令牌被篡改 | 401，节点不执行任务 |

### 5.4 迁移专项（TC-v2.1-32）

`up → down → up` 各一次；down 后核对 V8_x 数据行数不变；备份恢复后 `db-audit` 7 项 PASS。

> **实测（2026-11-07）：PASS。** `scripts/v21/migrate-roundtrip.sh` 在 scratch 库 `robot_it_local`
> 上从空库真跑 V1..V9_4，再逐版本 down 到 V8_6、重放回 V9_4、备份恢复；61 项断言全 OK，
> 业务表数 66→65→64→62→60→66，`test_report`（V8_1）与 `project` 行数全程不变，
> 恢复后 `db-audit` 7 项 PASS。证据：`docs/evidence/v2.1/TC32-MIG/README.md`。

---

## 6. 环境与样本

| 项 | 取值 | 说明 |
|---|---|---|
| 数据库 | MySQL `robot_dev`（业务样本）/ 迁移往返在 scratch 库 `robot_it_local`（本机） | 破坏性演练按拍板第 3 条授权，前后备份；TC-32 只动 scratch 库，真实业务库不参与 |
| 入口 | `:8001` | 与 v2.0 一致 |
| 第二节点 | 本机 `:8082` 模拟 | 真机验收归现场日（拍板第 4 条） |
| 浏览器 | 本机 Chrome（CDP） | 复用 `capture.mjs` |
| 示例用例 | SMPL-01（5–10 条，不映射业务） | 拍板第 1 条确认不违反 ADR-0013 |
| pytest 运行时 | **已就绪**（`~/venvs/pytest-kernel`：Python 3.12.3 / pytest 9.2.0 + pytest-json-report 1.5.0 + pytest-xdist 3.8.0） | 原「未就绪」记录已过期：A1 于 2026-11-06 落地（证据 `docs/evidence/v2.0/A1/README.md`），TC-v2.1-29 可实跑 |

---

## 7. 退出标准

1. 32 条 TC 全部有结论（PASS / FAIL / NOT-RUN）；NOT-RUN 写明原因与补测计划；**存在 NOT-RUN 时不得申请 v2.1 验收**。
2. 回归基线七条（780 / 239 / 219 / `db-audit` / 命令 diff / 历史快照 / 14 步门禁）全绿且条数不减。
3. 聚类确定性专项三次一致。
4. 四格式一致性专项三条全过。
5. 节点故障注入 4 条全过。
6. 迁移 `up → down → up` 实跑通过。
7. 每包证据目录 `docs/evidence/v2.1/<包>/README.md` 齐（命令 + 原始输出 + 结论）。
8. PRD §6 九条判定逐条有证据索引，缺一条即未达门。

---

## 8. 风险与应对

| 风险 | 应对 |
|---|---|
| 聚类在实际数据上碎片化 | 先跑历史数据做一次「簇数分布」观察，再定 40 字符截断长度；必要时退化为精确 `failure_reason` 分组（DEV-PLAN §9） |
| 无第二台真机 | 本机第二端口模拟跑全部开发期 TC；真机项记 `待现场`，与 v2.0 六个待现场同一次现场日 |
| xlsx 手工校验易漏 | 用 `python3 -c "import openpyxl"` 读单元格类型，断言数值列为 `n` 不是 `s` |
| 模板放开导致敏感字段外泄 | TC-v2.1-14 双负向 + TC-v2.1-18 四格式扫描，两道都要过 |
| 与 A8/B4 同批改动冲突 | 波次本应错开（W9 不早于 A8；B4 先收敛门禁）；**2026-11-07 实际：W9 已先行落地**，导出链路抽样待 A8 完成后补跑（见 DEV-PLAN §3 偏离记录），期间 TPLX 导出类 TC 不得判 PASS |

---

## 修订记录

| 日期 | 版本 | 改了什么 | 为什么改 |
|---|---|---|---|
| 2026-11-06 | v1.0 | 初版：32 条 TC（7 域）+ 6 层代码图例 + 4 波出口 + 4 项专项 + 8 条退出标准 + 5 条风险 | 承接 PRD v1.0 的 17 条 FR（每条至少 1 条 TC）与 DEV-PLAN v1.0 的 4 波 |
| 2026-11-06 | v1.1 | ① 新增 **§4.8 执行器与 runner 映射**：五个通道（BE/FE/UI/CHAIN/NEG）+ 32 条 TC 逐条落到「执行器 + 证据文件名」；② TC-09/11/12 改为**类 + 簇两级**判据（复用既有 `FailureClassifier`） | 大师要求测试计划落到可执行粒度；分类器已存在这一事实要求判据同步收紧到「类也要对」 |
| 2026-11-07 | v1.2 | ① §4.7 / §5.4 就地回写 **TC-31 / TC-32 实测结论**（36 项 / 61 项断言全 PASS，证据目录入）；② §6 订正 pytest 运行时「未就绪」→ **已就绪**（A1 已落地）；③ §6 数据库口径补 scratch 库；④ §8 补记 W9 先于 A8 落地的偏离与管制口径 | 原文是开工前的环境事实，实跑后已过期；证据要能被计划正文指到，而不是只躺在 evidence 目录里 |

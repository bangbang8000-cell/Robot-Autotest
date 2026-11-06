# v1.0 生产就绪 · ⑥ 测试报告

**文档编号** RA-REL-2026-v1.0-TR ｜ **版本** v1.0 ｜ **日期** 2026-11-06
**状态** 机器门复跑通过；TC 级沿用历史证据；门禁 G7 真值见 §4

> 本文件是 2026-11-06 W1 收口时补齐的版本流程第 ⑥ 道产物。**本轮重跑的是机器门**，
> 不是逐条 TC 重新执行；凡未重跑的 TC 一律标 `EVIDENCE-ONLY`，不得读作「本轮通过」。
> 真机 / 外部凭据 / 时间类条件见 §5，一律 `待现场`。

## 1. 结论

| 项 | 值 |
|---|---|
| 版本 / 批次 | v1.0 生产就绪 / B8 |
| TEST-PLAN 中识别到 TC | 43 条 |
| 本轮机器门 | 8 项全绿（见 §2） |
| 本轮逐条重跑 TC | 0（口径见上文） |
| 历史证据文件 | 337 份（§3 列前 18 份） |
| 门禁 G7 | 发版门禁，归大师拍板 |
| 当前结论 | **机器门通过、TC 证据齐备；门禁未判过项与真机项不冒充通过** |

## 2. 本轮机器门实测（2026-11-06，W1 收口批次）

| 门 | 命令 | 实测读数 | 结果 |
|---|---|---|---|
| 后端单测 | `mvn -B test` | 644 / 644 通过，0 失败 0 错误 | PASS |
| 前端单测 | `cd frontend && npm run test` | 30 文件 / 221 用例通过 | PASS |
| 前端类型与构建 | `npm run build（vue-tsc --noEmit）` | 0 类型错误，dist 产出成功 | PASS |
| 后端静态检查 | `mvn -B checkstyle:check` | 0 violations / BUILD SUCCESS | PASS |
| 数据库体检 | `bash scripts/db-audit.sh` | 8 项 PASS / 47 业务表 | PASS |
| 仓库守门 | `bash scripts/lint.sh` | PASS（含 Checkstyle 接线） | PASS |
| 统一入口实测 | `bash scripts/verify-entry.sh` | 12 / 12 PASS | PASS |
| WBS 自洽 | `python3 tools/check_wbs_consistency.py` | 130 包 / 87 已完成，五数自洽 | PASS |

> 原始日志由 `docs/evidence/v1.0/policy-20261106/gates.sh` 复跑重建（logs/ 被 .gitignore 忽略）。
> **2026-11-06 二轮复跑（本节读数为最终值）**：门禁从 10 步扩到 **11 步**（新增
> `11-editor_test`：`packages/editor` 有自己的 vitest 工程，frontend 的 vitest 扫不到它，
> 而 EDT-03/EDT-05 的回归用例正住在那里）。11 步 `GATES_RESULT FAILS=0`，实测读数：
> 后端 **Tests run: 780, Failures: 0**、frontend **37 文件 / 239 用例**、editor 包 **24 文件 / 219 用例**、
> `vue-tsc` 0 错误、`vite build` 通过（`EditorView` 809.56 kB）、`scripts/lint.sh` PASS、
> `check_release_docs` / `check_wbs_consistency`（130 包 / 124 已完成 / 待验 0）/ `check_md_tables` / `git diff --check` 均 PASS。


## 3. 历史证据清单

共 337 份，以下列前 18 份（相对仓库根）：

- `docs/evidence/v1.0/ast01-it/README.md`
- `docs/evidence/v1.0/ast01-it/backend-test-20261106.log`
- `docs/evidence/v1.0/ast01-it/device-asset-it-20261106.log`
- `docs/evidence/v1.0/ast01-it/font-20261106.log`
- `docs/evidence/v1.0/ast01-it/frontend-build-20261106.log`
- `docs/evidence/v1.0/ast01-it/frontend-test-20261106.log`
- `docs/evidence/v1.0/ast01-it/frontend-tsc-20261106.log`
- `docs/evidence/v1.0/ast01-it/lint-20261106.log`
- `docs/evidence/v1.0/ast01-it/release-docs-20261106.log`
- `docs/evidence/v1.0/ast01-it/wbs-20261106.log`
- `docs/evidence/v1.0/cas02-ui/backend-test-20261106.log`
- `docs/evidence/v1.0/cas02-ui/backend-test-testsetcontroller-20261106.log`
- `docs/evidence/v1.0/cas02-ui/cas02-ui-20261106.md`
- `docs/evidence/v1.0/cas02-ui/cas02-ui-vitest.log`
- `docs/evidence/v1.0/cas02-ui/font-20261106.log`
- `docs/evidence/v1.0/cas02-ui/frontend-build-20261106.log`
- `docs/evidence/v1.0/cas02-ui/frontend-test-20261106.log`
- `docs/evidence/v1.0/cas02-ui/frontend-tsc-20261106.log`
- … 其余 319 份见 `docs/evidence/v1.0/`

## 4. TC 台账（口径：EVIDENCE-ONLY）

| TC | 本轮结果 | 判定依据 |
|---|---|---|
| TC-v1.0-101 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-102 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-103 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-104 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-105 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-106 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-107 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-201 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-202 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-203 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-204 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-205 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-206 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-207 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-208 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-209 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-210 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-211 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-212 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-301 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-302 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-303 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-304 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-305 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-306 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-401 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-402 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-403 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-404 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-501 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-502 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-503 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-504 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-505 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-506 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-507 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-601 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-602 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-603 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-604 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-605 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-606 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v1.0-607 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |

## 5. 遗留缺口与未验证项（不自评通过）

- 门禁 G7：发版门禁，归大师拍板。
- 真机 / 外部凭据类（DUT、交换机、GitHub runner、pabot）一律 `待现场`；ADR-0013 禁跑令有效。
- 条件类（4 并发、打印链路）与时间类（连续运行）不具备复跑条件，见 `docs/plan/门禁核验纪要-G2-G5-20261106.md`。
- v2.0 的 W2/W3 受 G5/G4 前置约束，本报告不将其计入任何「通过」结论。

## 6. 修订记录

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-11-06 | v1.2 | 机器门读数按最终门禁日志订正：后端 642→644、前端 220→221，WBS 补 87/130；不沿用估算值 |
| 2026-11-06 | v1.3 | 二轮真实链路复跑：权限矩阵抽样 20 条真 HTTP、编辑器权限真值断言、词条元数据真库写权限、截图基线 16/16 全跑；`IAM-05`/`EDT-05`/`EDT-04`/`UIX-04` 转已完成；门禁 10→11 步，读数 780 / 239 / 219；新增 §7 记录两个真缺陷与「抽样不等于全量」的口径 |
| 2026-11-06 | v1.1 | TC 台账改为复用 `tools/check_release_docs.py::extract_ids()`，补齐区间展开与两种编号形态（v1.0 生成器漏 30 条被机检判 FAIL） |
| 2026-11-06 | v1.0 | 首次补齐；按「机器门实测 + 历史证据 + 门禁真值」三层写实，不用通过模板 |

---

## 7. 二轮回写：真实链路复跑（2026-11-06 晚）

> 本版 §4 里 `EVIDENCE-ONLY` 的五组（权限矩阵、导出重算、响应式截图、编辑器权限、词条元数据）
> 本轮有 4 组真的跑了。原表保留。

### 7.1 从 EVIDENCE-ONLY 转为实测

| 项 | 原口径 | 本轮实测 | 证据 |
|---|---|---|---|
| `IAM-05` 越权测试集 | 控制器级 MockMvc 36 条 | **真跑 20 条**：经 `:8001` → `:8081`，真库真账号 PBKDF2 登录拿令牌；含防枚举（存在/不存在项目同 403 同响应体）、`X-Project-Id` 非数字 400、无角色用户列表空集 | `docs/evidence/v1.0/auth-http-e2e/`（36/36，连跑两轮幂等） |
| `HRD-01` 权限矩阵 | 113 用例控制器级 + `authz-matrix.csv` | **真跑抽样 20 条**（跨项目 / 防枚举 / 角色边界 / 无角色四类），结论与 CSV 一致 | 同上 + `README.md` §边界说明（说明抽样不等于全量） |
| `EDT-05` 只读 403 | 需真账号 | **真跑**：`GET /api/editor/permissions` 真值断言 + 写工作区 403 vs 200 差分 | 同上 |
| `EDT-04` 词条元数据 | `V2_5` 真实库写权限未验 | **真跑**：`flyway=2.5`、种子 29 条（BuiltIn 18）、访客可检索、工程师写平台级 403 / 管理员 200 | 同上 |
| `UIX-04` 截图基线 | NOT-RUN | **真跑 16/16**：8 页 × 2 主题，1440 利用率与 1280 `scrollWidth` 均达标 | `docs/evidence/v1.0/w7-design/` |
| `RPT-07` 导出重算 | 一轮已转已完成（pdf/docx 参数化） | 本轮未重跑 | — |

### 7.2 本轮修掉的两个真缺陷

**缺陷 1：权限异步到达后只读不生效。**
`RobotEditor` 的 `props.readonly` 只在初始化时读一次并编译进 `EditorState`。
真实页面上权限是 `GET /api/editor/permissions` **异步**拿的，宿主先渲染成可编辑、回来后才变只读 ——
此时 CodeMirror 仍可键入，`EDT-05`「只读时 CodeMirror 本身也拒绝键入」实际失效。
修法：只读扩展改走 `Compartment`，`watch(readonly)` 原地 `reconfigure`，不销毁实例、不丢 undo 历史。

**缺陷 2：1280 视口整页横滚（UIX-04 判据不达标）。**
视图根 `.page` 是单列 grid 且未写 `grid-template-columns`，单列轨道按 `max-content` 计算，
被宽表格撑到 1144px；1280 下 `scrollWidth 1392 > 1280`，亮暗共 4 张截图不达标（首轮 12/16）。
修法：`.page { grid-template-columns: minmax(0,1fr) }` + `.page > * { min-width: 0 }`，
超宽表格的横向滚动交回 `.table-scroll` 自己处理。修后 **16/16**。

### 7.3 验收脚本的幂等是被迫做出来的

第一版验收脚本连跑两轮会红，因为脚本自己改了现场：PM 把 `nobody` 加成成员后没撤销、
工程师建用例用固定 code 撞唯一键、管理员建平台级词条同理。三处都补了「断言完立刻恢复」。
**验收脚本不能重复跑，它记录的 PASS 就只是一次快照**，下一轮回归没人敢信。

### 7.4 仍未收口

- `HRD-01` 的 112 格矩阵**没有全量重跑**，本轮的 20 条是抽样；完整矩阵仍是 113 用例 + CSV。
- 审计 10 类留痕、UAT 8 条、回滚 RTO、容量基线三项数字仍属缺外部条件（见 WBS 待现场 6 包）。
- G7 / G8 **不自评通过**。

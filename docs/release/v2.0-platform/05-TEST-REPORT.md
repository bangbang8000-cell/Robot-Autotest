# v2.0 平台化 · ⑥ 测试报告（W1 分层实测台账）

**文档编号** RA-REL-2026-020-TR ｜ **版本** v2.0-platform ｜ **日期** 2026-11-06
**状态** W1 六包已实现并本机实测；**W2–W7 未开工、G8 未判过**
**说明** 本文件在 2026-11-06 W1 收口时由「计划态快照」改写。结果分三档：
`PASS` = 本轮有直接机器证据；`PARTIAL` = 单测/纯逻辑通过但真实页面、实库写入、WS 握手或键盘 E2E 未跑；
`NOT-RUN` = 前置未满足或演练未做。**PARTIAL 不得读作通过，W1 结果不得读作 v2.0 通过。**

---

## 1. 结论

| 项 | 值 |
|---|---|
| 开工日期 | 2026-11-06 |
| W1 相关 TC | 11 条（TC-v2.0-10~17、65~67） |
| PASS | **3**（TC-v2.0-10 / 16 / 65） |
| PARTIAL | **7**（单测或逻辑通过，E2E / 实库 / WS 未跑） |
| 失败 | 0 |
| NOT-RUN | **57**（TC-v2.0-67 回滚演练 1 条 + W2–W7 的 56 条） |
| 本轮机器门 | 八步门禁 `FAILS=0`；入口实测 `12/12`；版本机检 `FAIL 0` |
| 当前结论 | **W1 机器门全绿；v2.0 未通过 G8，W2–W7 未开工** |

---

## 2. 执行台账

| TC | 覆盖 FR | 结果 | 证据位置 | 备注 |
|---|---|---|---|---|
| TC-v2.0-01 | FR-v2.0-01 | NOT-RUN | — | 待 W2 |
| TC-v2.0-02 | FR-v2.0-02 | NOT-RUN | — | 待 W2 |
| TC-v2.0-03 | FR-v2.0-03 | NOT-RUN | — | 待 W2 |
| TC-v2.0-04 | FR-v2.0-04 | NOT-RUN | — | 待 W2 |
| TC-v2.0-05 | FR-v2.0-05 | NOT-RUN | — | 待 W2 |
| TC-v2.0-06 | FR-v2.0-06 | NOT-RUN | — | 待 W2 |
| TC-v2.0-07 | FR-v2.0-06 | NOT-RUN | — | 待 W2 |
| TC-v2.0-08 | FR-v2.0-06 | NOT-RUN | — | 待 W2 |
| TC-v2.0-09 | FR-v2.0-04 | NOT-RUN | — | 待 W2 |
| TC-v2.0-10 | FR-v2.0-07 | PASS | `config/navigation.spec.ts` | 6 域 / 层级 ≤2 / 能力过滤，单测断言 |
| TC-v2.0-11 | FR-v2.0-08 | PARTIAL | `navigation.spec.ts` + `ForbiddenView` + 路由守卫 | 4 角色覆盖；直达无权限路由 E2E 未跑 |
| TC-v2.0-12 | FR-v2.0-09 | PARTIAL | `config/commandPalette.spec.ts` | 搜索/最近 5/收藏逻辑通过；键盘 E2E 未跑 |
| TC-v2.0-13 | FR-v2.0-10 | PARTIAL | `SystemSettingServiceTest` | 默认值/403/非法值/审计通过；实库写入未跑 |
| TC-v2.0-14 | FR-v2.0-10 | PARTIAL | `SystemSettingServiceTest` | 项目/平台隔离通过；页面回显未跑 |
| TC-v2.0-15 | FR-v2.0-11 | PARTIAL | `BuildInfoServiceTest` + `/api/system/build-info` | 端点存在（verify-entry）；页面实渲未跑 |
| TC-v2.0-16 | FR-v2.0-12 | PASS | `config/changelog.spec.ts` + verify-entry `/CHANGELOG.md` 200 | 四类齐全/当前唯一/倒序，静态数据可达 |
| TC-v2.0-17 | FR-v2.0-13 | PARTIAL | `config/commandPalette.spec.ts` | 折叠/收藏持久化通过；切项目 UI 未跑 |
| TC-v2.0-18 | FR-v2.0-14 | NOT-RUN | — | 待 W3 |
| TC-v2.0-19 | FR-v2.0-15 | NOT-RUN | — | 待 W3 |
| TC-v2.0-20 | FR-v2.0-15 | NOT-RUN | — | 待 W3 |
| TC-v2.0-21 | FR-v2.0-16 | NOT-RUN | — | 待 W3 |
| TC-v2.0-22 | FR-v2.0-17 | NOT-RUN | — | 待 W3 |
| TC-v2.0-23 | FR-v2.0-18 | NOT-RUN | — | 待 W3 |
| TC-v2.0-24 | FR-v2.0-19 | NOT-RUN | — | 待 W3 |
| TC-v2.0-25 | FR-v2.0-20 | NOT-RUN | — | 待 W3 |
| TC-v2.0-26 | FR-v2.0-21 | NOT-RUN | — | 待 W3 |
| TC-v2.0-27 | FR-v2.0-21 | NOT-RUN | — | 待 W3 |
| TC-v2.0-28 | FR-v2.0-14 | NOT-RUN | — | 待 W3 |
| TC-v2.0-29 | FR-v2.0-18 | NOT-RUN | — | 待 W3 |
| TC-v2.0-30 | FR-v2.0-23 | NOT-RUN | — | 待 W4 |
| TC-v2.0-31 | FR-v2.0-24 | NOT-RUN | — | 待 W4 |
| TC-v2.0-32 | FR-v2.0-25 | NOT-RUN | — | 待 W4 |
| TC-v2.0-33 | FR-v2.0-26 | NOT-RUN | — | 待 W4 |
| TC-v2.0-34 | FR-v2.0-28 | NOT-RUN | — | 待 W4 |
| TC-v2.0-35 | FR-v2.0-28 | NOT-RUN | — | 待 W4 |
| TC-v2.0-36 | FR-v2.0-27 | NOT-RUN | — | 待 W4 |
| TC-v2.0-37 | FR-v2.0-30 | NOT-RUN | — | 待 W4 |
| TC-v2.0-38 | FR-v2.0-31 | NOT-RUN | — | 待 W4 |
| TC-v2.0-39 | FR-v2.0-24 | NOT-RUN | — | 待 W4 |
| TC-v2.0-40 | FR-v2.0-29 | NOT-RUN | — | 待 W4 |
| TC-v2.0-41 | FR-v2.0-32 | NOT-RUN | — | 待 W5 |
| TC-v2.0-42 | FR-v2.0-33 | NOT-RUN | — | 待 W5 |
| TC-v2.0-43 | FR-v2.0-34 | NOT-RUN | — | 待 W5 |
| TC-v2.0-44 | FR-v2.0-34 | NOT-RUN | — | 待 W5 |
| TC-v2.0-45 | FR-v2.0-35 | NOT-RUN | — | 待 W5 |
| TC-v2.0-46 | FR-v2.0-35 | NOT-RUN | — | 待 W5 |
| TC-v2.0-47 | FR-v2.0-36 | NOT-RUN | — | 待 W5 |
| TC-v2.0-48 | FR-v2.0-37 | NOT-RUN | — | 待 W5 |
| TC-v2.0-49 | FR-v2.0-38 | NOT-RUN | — | 待 W5 |
| TC-v2.0-50 | FR-v2.0-39 | NOT-RUN | — | 待 W5 |
| TC-v2.0-51 | FR-v2.0-40 | NOT-RUN | — | 待 W6 |
| TC-v2.0-52 | FR-v2.0-41 | NOT-RUN | — | 待 W6 |
| TC-v2.0-53 | FR-v2.0-42 | NOT-RUN | — | 待 W6 |
| TC-v2.0-54 | FR-v2.0-43 | NOT-RUN | — | 待 W6 |
| TC-v2.0-55 | FR-v2.0-44 | NOT-RUN | — | 待 W6 |
| TC-v2.0-56 | FR-v2.0-45 | NOT-RUN | — | 待 W6 |
| TC-v2.0-57 | FR-v2.0-46 | NOT-RUN | — | 待 W6 |
| TC-v2.0-58 | FR-v2.0-46 | NOT-RUN | — | 待 W6 |
| TC-v2.0-59 | FR-v2.0-47 | NOT-RUN | — | 待 W7 |
| TC-v2.0-60 | FR-v2.0-48 | NOT-RUN | — | 待 W7 |
| TC-v2.0-61 | FR-v2.0-49 | NOT-RUN | — | 待 W7 |
| TC-v2.0-62 | FR-v2.0-50 | NOT-RUN | — | 待 W7 |
| TC-v2.0-63 | FR-v2.0-51 | NOT-RUN | — | 待 W7 |
| TC-v2.0-64 | FR-v2.0-52 | NOT-RUN | — | 待 W7 |
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
- **G8 十条判据未核验**：报告双实体（G5）、双内核（G4）、C2/C3/C5 三样外部样本仍缺，
  W2/W3 不得开工；`G-授权` 与 ADR-0013 禁跑令未解除。见 `docs/plan/门禁核验纪要-G2-G5-20261106.md`。

## 4. 本轮机器门实测（2026-11-06）

| 门 | 命令 | 结果 |
|---|---|---|
| 八步门禁 | `bash docs/evidence/v1.0/policy-20261106/gates.sh` | 10 步全 PASS，`GATES_RESULT FAILS=0` |
| 统一入口实测 | `bash scripts/verify-entry.sh` | 12 / 12 PASS |
| 版本流程机检 | `python3 tools/check_release_docs.py` | FAIL 0 项（v2.0 PASS） |
| 后端单测 | `mvn -B test` | 通过（含 W1 新增 3 个测试类 + V7 回填守门） |
| 前端单测 | `npm run test` | 通过（含 navigation / commandPalette / changelog 三套新用例） |
| 数据回填 | `bash scripts/db-migrate.sh` | V7_7 / V7_8 应用后 3 项断言均为 0 |

> 门禁原始日志落在 `docs/evidence/v1.0/policy-20261106/logs/`（被 `.gitignore` 忽略，可由 gates.sh 复跑重建）。
> 逐项修复与证据见 `docs/evidence/v1.0/w1-delivery/README.md`。

---

## 修订记录

| 日期 | 版本 | 改了什么 | 为什么改 |
|---|---|---|---|
| 2026-11-06 | v1.0 | 初版：67 条 TC 计划态台账，全部 NOT-RUN | 版本流程要求六份产物齐全，本版尚未开工 |
| 2026-11-06 | v1.1 | **推翻 v1.0 的「未开工 / 全部 NOT-RUN」**：W1 六包已实现并本机实测，11 条 W1 TC 拆为 PASS 3 / PARTIAL 7 / NOT-RUN 1；新增 §4 机器门实测；标题与状态行同步 | 七道流程第 ⑦ 道文档回写；按「被实测推翻的假设逐条改掉并留修订记录」办理，不悄悄改数字 |

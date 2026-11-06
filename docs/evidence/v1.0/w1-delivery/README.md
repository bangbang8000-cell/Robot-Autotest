# W1 开发交付证据（2026-11-06）

**文档编号** RA-EVD-2026-W1-001 ｜ **归属** `docs/evidence/v1.0/w1-delivery/` ｜ **对应授权** `docs/plan/决策纪要-20261106.md` §9

## 1. 这一包证据回答什么

回答「大师 15:23 给的一次性开发授权，学徒到底做了什么、凭什么说做完了、哪些没做完」。
**进度真源是 `docs/plan/WBS-STATUS.md`，本文只做证据索引，不另立一套状态。**

## 2. 交付范围

| 批次 | 包 | 产物 |
|---|---|---|
| W1 后端 | ENVX-02 / NAV-02 / NAV-03 | `HealthController`（`/healthz` 存活 + `/api/health` 就绪分离）、`SystemSettingService` 平台/项目分级 + 白名单 + 审计、`BuildInfoService` 构建信息与四项自检；3 个新单测类 |
| W1 前端 | NAV-01 / NAV-04 | 6 域 IA + 能力驱动过滤、系统设置页、关于页、版本迭代页、403 页、命令面板、顶栏项目选择器、中英文键；3 套新 vitest 用例 |
| W1 入口 | ENVX-01 | `scripts/serve-entry.mjs`（零依赖静态托管 + SPA fallback + `/api`·`/ws` 反代 + 缓存策略）、`verify-entry.sh`（12 项）、`deploy.sh`、`docs/ops/端口矩阵.md`、nginx TLS 与 systemd unit 样例 |
| P1 收口 | AST-04 / CAS-03 / UX-04 / EXEC-01 / PRJ-01 / WS-02 | `V7_7` owner 通用回填 + `V7_8` 技术域回填（含 down 与静态守门）、三案 B 书面定案、Checkstyle 真实接入 |
| 流程补齐 | 七版 ⑥ | v0.1–v1.0 七份 `05-TEST-REPORT.md`（机器门实测 + 历史证据 + 门禁真值三层写实） |

## 3. 最终机器门读数（第四次复跑）

```
PASS 01-subset_font / 02-frontend_test / 03-type_check / 04-frontend_build
PASS 05-backend_test / 06-lint / 07-release_docs / 08-wbs_consistency
PASS 09-md_tables / 10-diff_check
GATES_RESULT FAILS=0
```

| 项 | 实测读数 | 出处 |
|---|---|---|
| 后端单测 | `Tests run: 644, Failures: 0, Errors: 0` / BUILD SUCCESS | `logs/05-backend_test.log` |
| 前端单测 | `30 passed (30)` 文件 / `221 passed (221)` 用例 | `logs/02-frontend_test.log` |
| 前端构建 | `✓ built in 7.59s`，0 类型错误 | `logs/04-frontend_build.log` |
| Checkstyle | `src/main` 0 violations | `logs/06-lint.log` |
| 版本文档机检 | 8 版 / `FAIL 0 项`（v2.0 PASS，七版 IDLE 不计违规） | `tools/check_release_docs.py` |
| WBS 自洽 | 130 包 / 87 已完成 / 8 待验 / 7 待现场 / 28 未开始 / 0 进行中 | `tools/check_wbs_consistency.py` |
| Markdown 表格 | 7 份文档 / 149 表格块 / 1201 数据行全一致 | `policy-20261106/check_md_tables.py` |
| 统一入口 | `PASS=12 FAIL=0` | `scripts/verify-entry.sh` |
| 数据回填 | `domain_tag` 空值 0 / `owner_id` 为空 0 / 旧 owner 角色 0 | `p1-closeout/apply_migrations.py` |

> `logs/` 被 `.gitignore` 忽略，不是「证据丢失」，可用 `bash docs/evidence/v1.0/policy-20261106/gates.sh` 原地重建。
> 8001 入口与 8081 后端在收口时均在运行：`node scripts/serve-entry.mjs`（pid 972670）、
> `java -jar backend/target/robot-autotest-backend-0.1.0-SNAPSHOT.jar`（pid 972542）。

## 4. 门禁四轮收敛（逐条留痕，不悄悄改）

第一次 `FAILS=3`，之后 lint 又分层暴露三项，全部修复后才全绿。**没有任何一次是靠放宽守门规则通过**。

| 轮次 | 失败 | 根因 | 修复脚本（幂等） |
|---|---|---|---|
| 1 | `05-backend_test` | `V7BackfillTest.domainBackfill` 断言没剥掉 SQL 标识符反引号 | `fix_final_gates.py` |
| 1 | `06-lint`（色值） | 4 个新 Vue 页面 11 处硬编码 hex | `fix_colors.py`（改语义 token `--ok-border` / `--fail-border` / `--panel`） |
| 1 | `07-release_docs` | 七版报告 TC 提取用自制正则，没展开区间 / 漏非填充编号，v0.1 缺 30 条 | `fix_final_gates.py`（生成器改为直接加载 `check_release_docs.extract_ids()`，消除口径漂移）+ `fix_report_numbers.py` |
| 2 | `06-lint`（i18n） | 版本迭代页把 CHANGELOG 中文小节标题写进代码，5 处硬编码中文 | `fix_i18n_changelog.py`（数据文件加语言无关 `<!-- added -->` 锚点；代码零中文，显示走 `t()`；保留旧格式回退） |
| 3 | `06-lint`（token） | 10 个新 px 值不在白名单 | `fix_px_tokens.py`（**不扩白名单**，收敛到已有档位；三页 max-width 统一 999px） |
| 4 | `06-lint`（表格） | 关于页依赖清单用原生 `<table>` | `fix_about_table.py`（按 UX-05 改走 `DataTable` 壳，不加白名单） |

所有脚本均「内容匹配 + 命中次数断言 + 完成标记」，实测首跑 PASS、重跑 SKIP，不按行号定位。

## 5. 文档回写（七道流程第 ⑦ 道）

`finalize_docs.py` 与 `update_v2_report.py` 共订正 9 份规范性 / 历史文档，逐条留修订记录：

- **活文档直接订正**：`docs/release/README.md`（v2.25 废止 v2.24 三处假设）、中长期规划（规模口径 96+34 候选 → **130 已立项**，补记二）、`00-RESEARCH.md` 状态行；
- **v2.0 ⑥ 测试报告**由「计划态全部 NOT-RUN」改写为分层台账：W1 的 11 条拆成 **PASS 3 / PARTIAL 7 / NOT-RUN 1**，其余 56 条保持 NOT-RUN，新增 §4 机器门实测；
- **历史快照保留原文 + 演进补记**：PRD / DEV-PLAN / TEST-PLAN 末尾勾定补记、`现状复核与文档更新评估` v1.2、`v2.0-裁决建议表` §6.5、`决策纪要` §11；
- **读数订正**：七版报告里 642→**644**、220→**221**（按最终门禁日志，不沿用估算值），留 v1.2 修订记录。

## 6. 诚实边界（这些没做，不得读作完成）

- 没跑浏览器 E2E、没带登录态打实库写入、没做 WebSocket 101 握手、没做部署→回滚演练 → v2.0 报告对应 TC 标 `PARTIAL` / `NOT-RUN`；
- 七版报告的 TC 级是**历史证据 `EVIDENCE-ONLY`**，本轮只重跑机器门；七版在 `check_release_docs.py` 中为 `IDLE`（缺 ① 调研 / ② 评估产物，按流程 §9 不计违规）；
- **G4 / G5 / G8 未判过**；W2/W3 受 G5/G4 与 C2（真实 pytest 工程）/ C3（真实 RF 工程）/ C5（对外字段白名单）约束未开工，学徒不造玩具工程顶替；
- `G-授权` 与 ADR-0013 禁跑令未解除；发版、破坏性变更、对外口径仍归大师。

## 7. 复跑命令

```bash
cd ~/robot-autotest
bash docs/evidence/v1.0/policy-20261106/gates.sh   # 八步 + 2 项，期望 FAILS=0
bash scripts/verify-entry.sh                       # 期望 PASS=12 FAIL=0
python3 tools/check_release_docs.py                # 期望 FAIL 0 项
python3 tools/check_wbs_consistency.py             # 期望 130 包自洽
python3 tools/check_i18n.py && python3 tools/check_colors.py && python3 tools/check_tokens.py
```

修复脚本重跑应全部 `SKIP`：`fix_final_gates.py` / `fix_colors.py` / `fix_i18n_changelog.py` /
`fix_px_tokens.py` / `fix_about_table.py` / `finalize_docs.py` / `update_v2_report.py` / `fix_report_numbers.py`。

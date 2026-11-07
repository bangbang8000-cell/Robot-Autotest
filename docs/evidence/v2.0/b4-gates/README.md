# B4 · 门禁收敛为单份 `scripts/gates.sh`

- 执行日期：2026-11-07
- 基线提交：`7a6a604`（改动前的 HEAD）
- 产物：`scripts/gates.sh`（唯一门禁）、`scripts/browser-regression.mjs`（第 13 步执行器）、
  `docs/evidence/gates/logs/`（每步一份日志 + `00-full-run.log` 全量）

## 一、结论

| B4 判据 | 实测 | 证据 |
|---|---|---|
| 仓内只剩一份 `gates.sh` 定义 | **PASS**：全仓 `grep -r 'run 13 browser_regression'` 只命中 `scripts/gates.sh`；两份副本已删 | 本节下方「删除清单」 |
| 第 13 步浏览器回归接入 | **PASS**：真实 Chrome 9222 + `:8001`，5 路由 × 2 视口 = **40 项断言，0 失败** | `docs/evidence/gates/logs/13-browser_regression.log` |
| 第 14 步报告敏感扫描接入 | **PASS**：无 EXTERNAL 样本 → 走扫描器自校验，白样本 0 命中 / 黑样本命中 IP/COMMAND/CREDENTIAL/ABS_PATH | `docs/evidence/gates/logs/14-report_sensitive.log` |
| 两步各自有负向自检（拿掉必须变红） | **PASS**：`--selfcheck` 四项全过（含「删步骤 13 / 14 各造一份临时副本 → 结构自检必红」） | 本 README §三 |
| 全量门禁 | **PASS**：16 步 + 结构自检，`GATES_RESULT FAILS=0`，合计 **145 秒** | `docs/evidence/gates/logs/00-full-run.log` |
| v2.1 TPLX-03 四格式扫描进门禁 | **PASS**：第 16 步，4 格式各 21 字段、集合与顺序一致 | `docs/evidence/gates/logs/16-report_consistency.log` |

## 二、步骤编号（编号是判据的一部分，不得重排）

| 步 | 名称 | 本次读数 |
|---|---|---|
| 01 | `subset_font.py` | PASS 8s |
| 02 | `frontend` vitest | PASS 19s，**42 文件 / 276 用例** |
| 03 | `vue-tsc --noEmit` | PASS 16s |
| 04 | `vite build` | PASS 11s |
| 05 | `mvn -o -B test` | PASS 47s，**882 / 882**，0 失败 0 错误 |
| 06 | `scripts/lint.sh` | PASS 18s（含 14 个 `check_*` 守门） |
| 07 | `check_release_docs.py` | PASS 0s |
| 08 | `check_wbs_consistency.py` | PASS 0s |
| 09 | `check_md_tables.py` | PASS 0s |
| 10 | `git diff --check` | PASS 0s |
| 11 | `packages/editor` vitest | PASS 7s |
| 12 | `db-audit.sh`（`robot_dev`） | PASS 1s，7 项 PASS / 非 utf8mb4 表 0 |
| 13 | **浏览器回归**（B4 新增） | PASS 13s，40 项 / 0 失败 |
| 14 | **报告敏感扫描**（B4 新增） | PASS 0s |
| 15 | `check_css_vars.py` | PASS 0s，108 个变量全部有定义 |
| 16 | `check_report_consistency.py`（TPLX-03） | PASS 0s，4 格式 × 21 字段一致 |
| 17 | **结构自检**（步骤 13/14 缺失即红） | PASS |

第 13 步断言口径（`scripts/browser-regression.mjs`）：

- 路由守卫：`/` 允许重定向到 `/overview`，其余路由要求 `location.pathname` 原样命中、且不得落到 `/login`；
- 主容器：`.app-main` 存在；
- 1440：内容区利用率 = `.app-main` 宽度 / `innerWidth` **≥ 78%**（本次 5 页均为 86.1%）；
- 1280：`documentElement.scrollWidth ≤ innerWidth + 1`（本次 5 页均 1280 / 1280）；
- JS 异常：`Runtime.exceptionThrown` 为 0。

## 三、负向自检（`bash scripts/gates.sh --selfcheck`）

```
SELFCHECK_13 PASS        # 真实页面注入 1600px 宽块 → scrollWidth=1600 > 1280，检测器判红
SELFCHECK 13 PASS（注入横滚被判红）
SELFCHECK 14 PASS（黑样本命中、白样本 0 命中）
STRUCT_FAIL 缺少步骤 13 browser_regression
SELFCHECK 删步骤 13 PASS（删了变红）
STRUCT_FAIL 缺少步骤 14 report_sensitive
SELFCHECK 删步骤 14 PASS（删了变红）
SELFCHECK_RESULT FAILS=0
```

实现方式：`--selfcheck` 用 `sed` 生成两份临时副本，分别删掉 `run 13` / `run 14` 行，
再调用 `check_structure`；只有「删掉后确实非 0」才记 PASS。这样「把步骤拿掉」不是口头约定，
而是每次都能重放的判据。

## 四、复跑

```bash
cd ~/robot-autotest
bash scripts/gates.sh                 # 全量，判据 GATES_RESULT FAILS=0
bash scripts/gates.sh --selfcheck     # 只跑 13/14 负向自检，判据 SELFCHECK_RESULT FAILS=0
node scripts/browser-regression.mjs   # 单跑浏览器回归（需 Chrome 9222 + :8001）
```

前置：`~/.config/robot-autotest/env` 提供 `DB_PASSWORD` / `QA_E2E_PASSWORD`；Chrome 以
`--remote-debugging-port=9222` 运行；统一入口 `:8001` 已起。缺任一前置时第 13 步会 FAIL，
这是有意的——门禁必须看得见真实页面，不能因为环境缺失就静默跳过。

## 五、删除清单（两份副本 + 引用改写）

| 删除文件 | 说明 |
|---|---|
| `docs/evidence/v1.0/longhist/gates.sh` | 12 步模板版 |
| `docs/evidence/v1.0/policy-20261106/gates.sh` | 可跑的 10 步版（历史证据里引用的就是它） |

指向旧路径的引用已批量改为 `bash scripts/gates.sh`；旧日志路径
`docs/evidence/v1.0/policy-20261106/logs/` 统一改为 `docs/evidence/gates/logs/`（历史读数仍保留在
原证据 README 中，不改数字）。`longhist` 版的 12 步与 `policy` 版的 10 步差异已并进本脚本
（editor / db_audit / css_vars / 四格式都保留）。

## 六、已知限制

1. 第 14 步在仓库内没有 EXTERNAL 导出样本，当前等价于「扫描器自校验」。等 A8 产出真实
   对外样本后应改为扫真文件（脚本已写好分支，样本一出现自动切换）。
2. 第 13 步的 5 条路由是主链路（`/overview`、`/runs`、`/assets`、`/reports/templates`、`/nodes`），
   不含登录后深层的报告对比 / 趋势页；B8 会在 `scripts/browser-regression.mjs` 里追加这两页，
   不新开文件。
3. `grep -r` 里 `docs/plan/*` 的**历史纪要**保留「当时的 10 步 / 12 步」读数不改数字，
   与 `docs/plan/决策纪要-20261104.md` 同类处理（原文不动，由新纪要声明被取代）。

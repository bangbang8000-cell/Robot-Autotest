#!/usr/bin/env bash
# UX-05 第一批（表格规范壳 + 表格主题接管）证据复现脚本，2026-11-05。
#
# 在 WSL 权威副本项目根执行：
#   bash docs/evidence/v0.5/ux05-20261105/run-all.sh
#
# 逐条实跑并把 stdout 落盘为同目录 .log，同时打印每条命令的真实 rc。
# 写成文件而不是 `wsl.exe bash -lc '内联'`：内联时 $? 会被 Windows 命令行层
# 先展开一次，rc 恒等于 0，证据会变成假绿（本项目踩过这个坑）。

cd /home/evergreen/robot-autotest || exit 99
OUT=docs/evidence/v0.5/ux05-20261105

echo "=== UX-05 第一批复现开始 ==="

# 1) 前端生产构建：先跑，刷新 frontend/dist 供体积守门读取
set -o pipefail
( cd frontend && npm run build ) 2>&1 | tail -40 > "$OUT/fe-build.log"
printf '%-30s rc=%s\n' 'fe-build' "$?"
set +o pipefail

# 2) 前端全量单测（本轮 109 → 127：壳 9 + 列宽 8 + 主题 1）
( cd frontend && npm run test ) > "$OUT/fe-test.log" 2>&1
printf '%-30s rc=%s\n' 'fe-test' "$?"

# 3) 视图 + 组件级回归（19 处表格迁壳后页面仍渲染同样的行）
( cd frontend && npx vitest run src/views src/components ) > "$OUT/view-test.log" 2>&1
printf '%-30s rc=%s\n' 'view+component-test' "$?"

# 4) 类型检查（泛型壳 props ↔ 各视图 DataTableColumns<T> 在这一层兜）
( cd frontend && npx vue-tsc --noEmit ) > "$OUT/fe-tsc.log" 2>&1
printf '%-30s rc=%s\n' 'fe-tsc' "$?"

# 5) 全量 lint：secret-scan / eslint / mvn compile / 静态守门（含本轮新增表格守门）
bash scripts/lint.sh > "$OUT/lint-sh.log" 2>&1
printf '%-30s rc=%s\n' 'lint-sh' "$?"

# 6) 产物体积：壳引入后懒加载共享分包仍在预算内
python3 tools/check_bundle_budget.py > "$OUT/bundle-budget.log" 2>&1
printf '%-30s rc=%s\n' 'bundle-budget' "$?"

# 7) 字体子集覆盖（本批新增注释汉字，已重生成子集 976 字）
python3 tools/subset_font.py --check > "$OUT/subset-font.log" 2>&1
printf '%-30s rc=%s\n' 'subset-font-check' "$?"

# 8) i18n 键集 / 硬编码中文 / 受控词表（壳新增的空态默认标题走 common.noData）
python3 tools/check_i18n.py > "$OUT/check-i18n.log" 2>&1
printf '%-30s rc=%s\n' 'check-i18n' "$?"

# 9) 交互态守门
python3 tools/check_states.py > "$OUT/check-states.log" 2>&1
printf '%-30s rc=%s\n' 'check-states' "$?"

# 10) token / px 白名单（壳的 max-height 复用已有 340px 档位）
python3 tools/check_tokens.py > "$OUT/check-tokens.log" 2>&1
printf '%-30s rc=%s\n' 'check-tokens' "$?"

# 11) 组件 import 对账（19 处 DataTable 全部有 import）
python3 tools/check_component_imports.py > "$OUT/component-imports.log" 2>&1
printf '%-30s rc=%s\n' 'component-imports' "$?"

# 12) 表格规范守门（本批新增）：19 处数据网格全走壳 / 原生 table 仅白名单 3 文件
python3 tools/check_tables.py > "$OUT/check-tables.log" 2>&1
printf '%-30s rc=%s\n' 'check-tables' "$?"

# 13) 上一条的负向证据：注入裸 NDataTable 必须报 FAIL 且指向被注入的文件
python3 docs/evidence/v0.5/ux05-20261105/neg-tables.py > "$OUT/neg-tables.log" 2>&1
printf '%-30s rc=%s\n' 'neg-tables' "$?"

# 14) 品牌守门（本批新增）：favicon 16px 可辨 / 侧栏与登录页都有标记 / 版本号只在关于
python3 tools/check_brand.py > "$OUT/check-brand.log" 2>&1
printf '%-30s rc=%s\n' 'check-brand' "$?"

# 15) 上一条的破坏性自检：文字/细描边/缺 link/版本号回退 都必须被拦
python3 tools/check_brand.py --self-test > "$OUT/check-brand-selftest.log" 2>&1
printf '%-30s rc=%s\n' 'check-brand-selftest' "$?"

# 14) 图标守门自检：动态 `:icon="emptyIcon"` 不得被误报成图标名（本批真实误报）
python3 tools/check_icons.py --self-test > "$OUT/icons-selftest.log" 2>&1
printf '%-30s rc=%s\n' 'icons-selftest' "$?"

# 15) 后端单元测试（本批未改后端，跑一遍排除连带）
( cd backend && mvn -B -q test ) > "$OUT/backend-mvn-test.log" 2>&1
printf '%-30s rc=%s\n' 'backend-mvn-test' "$?"

# 16) WBS 进度表自洽
python3 tools/check_wbs_consistency.py > "$OUT/check-wbs.log" 2>&1
printf '%-30s rc=%s\n' 'check-wbs' "$?"

# 17) 本轮改动清单
git -C /home/evergreen/robot-autotest diff --stat > "$OUT/diff-stat.log" 2>&1
printf '%-30s rc=%s\n' 'diff-stat' "$?"

echo "=== 结束：日志在 $OUT ==="

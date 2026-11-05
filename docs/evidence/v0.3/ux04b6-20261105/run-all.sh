#!/usr/bin/env bash
# UX-04b 第七批（登录/404/编辑器页收尾，业务视图迁移完成）证据复现脚本，2026-11-05。
#
# 在 WSL 权威副本项目根执行：
#   bash docs/evidence/v0.3/ux04b6-20261105/run-all.sh
#
# 逐条实跑并把 stdout 落盘为同目录 .log，同时打印每条命令的真实 rc。
# 写成文件而不是 `wsl.exe bash -lc '内联'`：内联时 $? 会被 Windows 命令行层
# 先展开一次，rc 恒等于 0，证据会变成假绿（本项目踩过这个坑）。

cd /home/evergreen/robot-autotest || exit 99
OUT=docs/evidence/v0.3/ux04b6-20261105

echo "=== UX-04b 第七批复现开始 ==="

# 1) 前端生产构建：先跑，刷新 frontend/dist 供体积守门读取
set -o pipefail
( cd frontend && npm run build ) 2>&1 | tail -40 > "$OUT/fe-build.log"
printf '%-30s rc=%s\n' 'fe-build' "$?"
set +o pipefail

# 2) 前端全量单测（本轮 104 → 109 条）
( cd frontend && npm run test ) > "$OUT/fe-test.log" 2>&1
printf '%-30s rc=%s\n' 'fe-test' "$?"

# 3) 视图 + 组件级回归（本批新增 LoginView.spec.ts）
( cd frontend && npx vitest run src/views src/components ) > "$OUT/view-test.log" 2>&1
printf '%-30s rc=%s\n' 'view+component-test' "$?"

# 4) 类型检查（NInput 的 value 窄类型、NButton 的 attr-type 都在这一层兜）
( cd frontend && npx vue-tsc --noEmit ) > "$OUT/fe-tsc.log" 2>&1
printf '%-30s rc=%s\n' 'fe-tsc' "$?"

# 5) 全量 lint：secret-scan / eslint / mvn compile / 静态守门
bash scripts/lint.sh > "$OUT/lint-sh.log" 2>&1
printf '%-30s rc=%s\n' 'lint-sh' "$?"

# 6) 产物体积：图标 / vendor / 懒加载分包 / 字体 / styleguide 泄漏
python3 tools/check_bundle_budget.py > "$OUT/bundle-budget.log" 2>&1
printf '%-30s rc=%s\n' 'bundle-budget' "$?"

# 7) 字体子集覆盖（本轮注释又动汉字，已重生成子集）
python3 tools/subset_font.py --check > "$OUT/subset-font.log" 2>&1
printf '%-30s rc=%s\n' 'subset-font-check' "$?"

# 8) i18n 键集 / 硬编码中文 / 受控词表
python3 tools/check_i18n.py > "$OUT/check-i18n.log" 2>&1
printf '%-30s rc=%s\n' 'check-i18n' "$?"

# 9) 交互态守门（本批删掉编辑器页 4 条无三态的 .editor-host__btn 规则）
python3 tools/check_states.py > "$OUT/check-states.log" 2>&1
printf '%-30s rc=%s\n' 'check-states' "$?"

# 10) token / px 白名单
python3 tools/check_tokens.py > "$OUT/check-tokens.log" 2>&1
printf '%-30s rc=%s\n' 'check-tokens' "$?"

# 11) 组件 import 对账（防「未注册组件静默不渲染」）
python3 tools/check_component_imports.py > "$OUT/component-imports.log" 2>&1
printf '%-30s rc=%s\n' 'component-imports' "$?"

# 12) 后端单元测试（本轮未改后端，跑一遍排除连带）
( cd backend && mvn -B -q test ) > "$OUT/backend-mvn-test.log" 2>&1
printf '%-30s rc=%s\n' 'backend-mvn-test' "$?"

# 13) 本轮改动清单
git -C /home/evergreen/robot-autotest diff --stat > "$OUT/diff-stat.log" 2>&1
printf '%-30s rc=%s\n' 'diff-stat' "$?"

echo "=== 结束：日志在 $OUT ==="

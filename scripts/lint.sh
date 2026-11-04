#!/usr/bin/env bash
# 后端 lint 占位：v0.1 只做密文扫描 + 编译；checkstyle 随 DOC-02（v0.2）接入。
# 前端有真实 eslint。这里不假装有后端静态检查。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p artifacts

echo "[lint] 密文扫描"
bash scripts/secret-scan.sh

if [[ -f frontend/package.json ]]; then
    command -v npm >/dev/null 2>&1 || { echo "[lint] 缺少 npm" >&2; exit 1; }
    if [[ ! -d frontend/node_modules ]]; then
        echo "[lint] frontend/node_modules 不存在，先 npm ci"
        (cd frontend && npm ci)
    fi
    echo "[lint] frontend: npm run lint"
    (cd frontend && npm run lint)
fi

if [[ -f backend/pom.xml ]]; then
    command -v mvn >/dev/null 2>&1 || { echo "[lint] 缺少 mvn" >&2; exit 1; }
    # 编译即最小静态检查（类型 / 语法）；checkstyle 未接入，见 docs/plan/WBS-STATUS.md 偏差记录
    echo "[lint] backend: mvn -B -DskipTests compile"
    (cd backend && mvn -B -q -DskipTests compile) 2>&1 | tee "$ROOT/artifacts/mvn-compile.log"
fi

# ---- 前端静态守门（v0.2 UX-01b / CAS-03 / v0.3 EDT 的产出） ----
# 2026-11-04 按各版 DEV-PLAN「完成判定」逐项实跑时发现：这些脚本一直只写在文档里，
# lint.sh 从未调用过它们 —— 写了守门却没接线，等于 CI 三绿是假绿。此处统一接线。
echo "[lint] 前端静态守门（tools/check_*.py）"
for guard in check_colors check_contrast check_i18n check_states check_tokens; do
    echo "[lint]   - tools/${guard}.py"
    python3 "tools/${guard}.py"
done
echo "[lint]   - tools/check_import_header.py（CAS-03 台账表头对账）"
python3 tools/check_import_header.py fixtures/case-ledger/ledger-sample-226.xlsx

# UX-04 美术 S1：构建产物体积 / styleguide 泄漏守门。dist 缺失时脚本自身打印
# [SKIP] 并返回 0，不会让 lint 假红；有违规才返回非 0，由 set -e 中断。
echo "[lint]   - tools/check_bundle_budget.py（UX-04 S1 产物体积守门）"
python3 tools/check_bundle_budget.py

# UX-04 新增的三条静态守门：token 清单对账 / 六预设主色派生 / 字体子集预算。
# 与上面五条一样，写了不接线就是假绿，所以统一挂在这里。
echo "[lint]   - tools/check_styleguide_tokens.py（styleguide ↔ theme.css 对账）"
python3 tools/check_styleguide_tokens.py
echo "[lint]   - tools/gen_accent_tokens.py --check（六预设 WCAG 派生）"
python3 tools/gen_accent_tokens.py --check
echo "[lint]   - tools/subset_font.py --check（打包字体子集预算）"
python3 tools/subset_font.py --check

echo "[lint] PASS"

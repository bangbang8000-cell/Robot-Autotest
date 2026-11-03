#!/usr/bin/env bash
# EDT-07 品牌扫描：产品面必须 0 命中（白名单除外），归属面逐条登记
#
# 用法：  bash scripts/brand-scan.sh            # 打印结果，产品面有违规即退出 1
#         bash scripts/brand-scan.sh > docs/evidence/v0.3/edt07-brand-scan.log 2>&1
#
# 口径：
#   · 产品面 = 会被打包/运行给用户看到的部分（含 dist 构建产物）
#   · 归属面 = Apache-2.0 §4(b) 要求的版权/许可声明与内部规划文档
#   · 白名单 brand-allow.txt 每行 `路径前缀|命中文本|理由`
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

PATTERN='roboscope|viadee|roboflow'
ALLOW_FILE="brand-allow.txt"
PRODUCT_PATHS=(
  "packages/editor/src"
  "packages/editor/index.ts"
  "packages/editor/dist"
  "frontend/src"
  "frontend/dist"
  "backend/src"
)
ATTRIB_PATHS=(
  "LICENSE"
  "LICENSE-roboscope"
  "NOTICE"
  "packages/editor/LICENSE-roboscope"
  "packages/editor/NOTICE.md"
  "packages/editor/UPSTREAM.md"
  "packages/editor/README.md"
  "docs"
)

allow_prefix=()
allow_text=()
allow_reason=()
if [ -f "$ALLOW_FILE" ]; then
  while IFS='|' read -r prefix text reason; do
    case "$prefix" in ''|\#*) continue ;; esac
    allow_prefix+=("$prefix")
    allow_text+=("$text")
    allow_reason+=("${reason:-未填写理由}")
  done < "$ALLOW_FILE"
fi

scan() { # $1=path
  local p="$1" file line text
  [ -e "$p" ] || return 0
  grep -rInE -i "$PATTERN" "$p" 2>/dev/null | while IFS= read -r hit; do
    file="${hit%%:*}"
    line="${hit#*:}"
    text="${line#*:}"
    echo "$file|$text"
  done
}

is_allowlisted() { # $1=file $2=text
  local i
  for i in "${!allow_prefix[@]}"; do
    case "$1" in
      "${allow_prefix[$i]}"*) case "$2" in *"${allow_text[$i]}"*) return 0 ;; esac ;;
    esac
  done
  return 1
}

product_violations=0
product_allowed=0
product_total=0
attrib_total=0
missing_dist=0

echo "== 品牌扫描（EDT-07） =="
echo "扫描词：$PATTERN（大小写不敏感） 白名单：$ALLOW_FILE"
echo

echo "[产品面] 必须 0 命中（白名单除外）"
for p in "${PRODUCT_PATHS[@]}"; do
  if [ ! -e "$p" ]; then
    if [ "$p" = "packages/editor/dist" ] || [ "$p" = "frontend/dist" ]; then
      echo "  MISSING  $p（构建产物不存在，本次扫描不完整）"
      missing_dist=1
    fi
    continue
  fi
  scan "$p" | while IFS='|' read -r file text; do
    product_total=$((product_total + 1))
    if is_allowlisted "$file" "$text"; then
      echo "  ALLOW    $file ｜ $(echo "$text" | cut -c1-110)"
      product_allowed=$((product_allowed + 1))
    else
      echo "  VIOLATE  $file ｜ $(echo "$text" | cut -c1-110)"
      product_violations=$((product_violations + 1))
    fi
  done
done | tee /tmp/edt07-product.$$
product_violations=$(grep -c '  VIOLATE ' /tmp/edt07-product.$$ || true)
product_allowed=$(grep -c '  ALLOW ' /tmp/edt07-product.$$ || true)
product_total=$((product_violations + product_allowed))
rm -f /tmp/edt07-product.$$

echo
echo "[归属面] 允许保留（版权/许可声明与内部规划文档），逐条登记"
for p in "${ATTRIB_PATHS[@]}"; do
  [ -e "$p" ] || continue
  n=$(scan "$p" | cat | wc -l)
  attrib_total=$((attrib_total + n))
  [ "$n" -gt 0 ] && echo "  ATTRIB   $p（$n 处）"
done

echo
echo "== 汇总 =="
echo "产品面命中 : $product_total（放行 $product_allowed / 违规 $product_violations）"
echo "归属面命中 : $attrib_total"
echo "白名单条数 : ${#allow_prefix[@]}"
echo
if [ "$product_violations" -gt 0 ]; then
  echo "结论: FAIL（产品面存在未登记的 brand 残留）"
  exit 1
fi
if [ "$missing_dist" -eq 1 ]; then
  echo "结论: PASS（源码面清零；构建产物缺失，需先 build 再复扫）"
else
  echo "结论: PASS（产品面含 dist 0 命中）"
fi

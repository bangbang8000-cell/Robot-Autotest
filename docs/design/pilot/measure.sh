#!/usr/bin/env bash
# 两候选分别 build，量各 chunk 的原始/ gzip 体积，产物供对比报告引用。
set -e
cd "$(dirname "$0")"
npm run build:naive   > dist/build-naive.log 2>&1
npm run build:element > dist/build-element.log 2>&1
OUT=dist/measure.tsv
printf 'candidate\tfile\traw_bytes\tgzip_bytes\n' > "$OUT"
for lib in naive element; do
  for f in dist/$lib/assets/*; do
    [ -f "$f" ] || continue
    raw=$(wc -c < "$f")
    gz=$(gzip -9 -c "$f" | wc -c)
    printf '%s\t%s\t%s\t%s\n' "$lib" "$(basename "$f")" "$raw" "$gz" >> "$OUT"
  done
done
echo "=== TOTAL (gzip) ==="
awk -F'\t' 'NR>1 {s[$1]+=$4} END {for (k in s) printf "%s\t%d bytes gzip\n", k, s[k]}' "$OUT" | sort
echo "=== DETAIL ==="
column -t -s $'\t' "$OUT"

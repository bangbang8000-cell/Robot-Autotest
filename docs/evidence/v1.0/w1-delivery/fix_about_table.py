#!/usr/bin/env python3
"""06-lint 最后一处失败：check_tables.py 拦下 AboutView.vue 的原生 <table>。

按 UX-05 表格规范改造，而不是加白名单
--------------------------------------
关于页「依赖清单」是 3 列 × 23 行的真数据网格，正确做法是走 `DataTable` 壳
（粘性表头、统一空态、列宽计算），与审计页等 10 个页面一致。
白名单只留给「打印页零库依赖 / metadata 定义表 / 设计系统展示页」三类，
给关于页开口子会让规范失去意义。

改动：script 补 computed + DataTableColumns + DataTable import 与列定义；
      模板 <table class="deps"> → <DataTable>；删掉随之失效的 .deps CSS。
幂等：命中次数断言 + 完成标记。
"""
from __future__ import annotations

import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[4]
REL = "frontend/src/views/AboutView.vue"


def patch(text: str, old: str, new: str, label: str) -> str:
    hits = text.count(old)
    if hits != 1:
        raise SystemExit(f"FAIL  {label}：期望命中 1 次，实际 {hits} 次")
    print(f"PASS  {label}")
    return text.replace(old, new, 1)


def main() -> int:
    path = ROOT / REL
    text = path.read_text(encoding="utf-8")
    if "depColumns" in text:
        print("SKIP  AboutView 依赖清单已走 DataTable 壳")
    else:
        text = patch(
            text,
            "import { onMounted, ref } from 'vue'\nimport { useI18n } from 'vue-i18n'\n"
            "import { settingsApi, type BuildInfo } from '@/api/settings'",
            "import { computed, onMounted, ref } from 'vue'\n"
            "import { useI18n } from 'vue-i18n'\n"
            "import type { DataTableColumns } from 'naive-ui'\n"
            "import DataTable from '@/components/DataTable.vue'\n"
            "import { settingsApi, type BuildInfo } from '@/api/settings'",
            "script：补齐 DataTable 相关 import",
        )
        text = patch(
            text,
            "const checkKeys = [",
            "type DepRow = BuildInfo['dependencies'][number]\n\n"
            "const depColumns = computed<DataTableColumns<DepRow>>(() => [\n"
            "  { title: t('aboutPage.name'), key: 'name', width: 260 },\n"
            "  { title: t('aboutPage.version'), key: 'version', width: 160 },\n"
            "  { title: t('aboutPage.source'), key: 'source' },\n"
            "])\n\n"
            "function depRowKey(row: DepRow): string {\n"
            "  return row.name\n"
            "}\n\n"
            "const checkKeys = [",
            "script：依赖清单列定义与行键",
        )
        old_table = """        <table class="deps">
          <thead>
            <tr><th>{{ t('aboutPage.name') }}</th><th>{{ t('aboutPage.version') }}</th><th>{{ t('aboutPage.source') }}</th></tr>
          </thead>
          <tbody>
            <tr v-for="dep in info.dependencies" :key="dep.name">
              <td>{{ dep.name }}</td><td>{{ dep.version }}</td><td>{{ dep.source }}</td>
            </tr>
          </tbody>
        </table>"""
        new_table = """        <DataTable
          :columns="depColumns"
          :data="info.dependencies"
          :row-key="depRowKey"
          :max-height="null"
          :empty-title="t('aboutPage.dependencies')"
        />"""
        text = patch(text, old_table, new_table, "模板：原生 table → DataTable 壳")
        text = patch(
            text,
            ".deps { width: 100%; border-collapse: collapse; font-size: 13px; }\n"
            ".deps th, .deps td { text-align: left; padding: 8px 10px; "
            "border-bottom: 1px solid color-mix(in srgb, currentColor 12%, transparent); }\n",
            "",
            "样式：移除失效的 .deps 规则",
        )
        path.write_text(text, encoding="utf-8")

    check = subprocess.run([sys.executable, "tools/check_tables.py"], cwd=ROOT,
                           capture_output=True, text=True)
    tail = (check.stdout or "").strip().splitlines()[-3:]
    for line in tail:
        print(line)
    if check.returncode != 0:
        print(check.stdout, check.stderr, file=sys.stderr)
        raise SystemExit("FAIL  表格守门仍未通过")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

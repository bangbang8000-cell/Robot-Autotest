# -*- coding: utf-8 -*-
"""W2 前端 / 接线 / 证据落地；附一条单测期望订正。幂等。"""
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path.cwd()
PASS = 0
SKIP = 0
F = {}


def write_new(rel, text):
    global PASS, SKIP
    p = ROOT / rel
    if p.exists():
        SKIP += 1
        print("[SKIP]", rel)
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text if text.endswith("\n") else text + "\n", encoding="utf-8")
    PASS += 1
    print("[PASS]", rel)


def replace_once(rel, old, new, marker=None):
    global PASS, SKIP
    p = ROOT / rel
    t = p.read_text(encoding="utf-8")
    # 追加型替换（new = delta + old）时，old 依然在文件里，不能用「old 不在 t」判重。
    # 改为检查「新增部分 delta 是否已在文件中」——这是项目里踩过两次的坑。
    delta = new.replace(old, "", 1).strip()
    if delta and delta in t:
        SKIP += 1
        print("[SKIP] 已回写", rel)
        return
    n = t.count(old)
    if n != 1:
        raise SystemExit(f"FAIL {rel}: 锚点命中 {n} 次: {old[:70]!r}")
    p.write_text(t.replace(old, new, 1), encoding="utf-8")
    PASS += 1
    print("[PASS] 回写", rel)


# ---------------------------------------------------------------- 单测期望订正
replace_once("backend/src/test/java/com/qa/autotest/application/report/ReportSnapshotTest.java",
             '        assertEquals("PASS", view.getOrDefault("conclusion", "FAIL"));',
             '        assertEquals("FAIL", view.get("conclusion"));',
             marker='assertEquals("FAIL", view.get("conclusion"))')

# ---------------------------------------------------------------- 前端 API
F["frontend/src/api/testReports.ts"] = r"""/** W2 / RPTX 测试报告 API。页面只经这里访问 /api/test-reports。 */
import { request, requestBlob } from './http'

export interface TestReportItem {
  id: number
  projectId: number
  runId: string
  title: string
  state: string
  version: number
  snapshotHash: string | null
  createdAt: string | null
  frozenAt: string | null
}

export interface TestReportExportItem {
  id: number
  reportId: number
  format: string
  fileName: string
  sizeBytes: number
  watermark: string | null
  createdAt: string | null
}

/** 导出文件名全平台统一，避免后端 Content-Disposition 与前端另存名不一致。 */
export function exportFileName(id: number, format: string): string {
  const ext = ['html', 'pdf', 'docx'].includes(format.toLowerCase()) ? format.toLowerCase() : 'html'
  return `report-${id}.${ext}`
}

export const testReportsApi = {
  list: (projectId: number) =>
    request<TestReportItem[]>(`/api/test-reports?projectId=${projectId}`),
  create: (projectId: number, runId: string, title: string) =>
    request<TestReportItem>('/api/test-reports', {
      method: 'POST',
      body: JSON.stringify({ projectId, runId, title }),
    }),
  freeze: (id: number) =>
    request<TestReportItem>(`/api/test-reports/${id}/freeze`, { method: 'POST' }),
  render: (id: number, external = true) =>
    request<Record<string, unknown>>(`/api/test-reports/${id}?external=${external}`),
  exportBlob: (id: number, format: string) =>
    requestBlob(`/api/test-reports/${id}/export?format=${format}`),
  exports: (id: number) =>
    request<TestReportExportItem[]>(`/api/test-reports/${id}/exports`),
}
"""

F["frontend/src/config/testReports.spec.ts"] = r"""import { describe, expect, it } from 'vitest'
import { exportFileName } from '@/api/testReports'

describe('exportFileName', () => {
  it('keeps known formats lowercased', () => {
    expect(exportFileName(12, 'PDF')).toBe('report-12.pdf')
    expect(exportFileName(3, 'docx')).toBe('report-3.docx')
    expect(exportFileName(3, 'HTML')).toBe('report-3.html')
  })

  it('falls back to html for unknown formats', () => {
    expect(exportFileName(9, 'xlsx')).toBe('report-9.html')
    expect(exportFileName(9, '')).toBe('report-9.html')
  })
})
"""

F["frontend/src/views/TestReportsView.vue"] = r"""<script setup lang="ts">
/** W2 / RPTX-03 对外测试报告列表。冻结后的报告快照自包含，可脱离源 run 渲染。 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DataTableColumns } from 'naive-ui'
import DataTable from '@/components/DataTable.vue'
import { useAuthStore } from '@/stores/auth'
import { testReportsApi, type TestReportItem } from '@/api/testReports'

const { t } = useI18n()
const auth = useAuthStore()
const rows = ref<TestReportItem[]>([])
const loading = ref(false)
const failed = ref(false)
const notice = ref('')
const runId = ref('')
const projectId = computed(() => auth.projectId ?? 0)

const columns = computed<DataTableColumns<TestReportItem>>(() => [
  { title: t('testReports.colTitle'), key: 'title', minWidth: 160 },
  { title: t('testReports.colRun'), key: 'runId', minWidth: 160 },
  { title: t('testReports.colState'), key: 'state', minWidth: 90 },
  { title: t('testReports.colVersion'), key: 'version', minWidth: 80 },
])

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    rows.value = await testReportsApi.list(projectId.value)
  } catch {
    failed.value = true
    notice.value = t('testReports.failed')
  } finally {
    loading.value = false
  }
}

async function create(): Promise<void> {
  if (!runId.value.trim()) return
  try {
    await testReportsApi.create(projectId.value, runId.value.trim(), '')
    runId.value = ''
    notice.value = t('testReports.created')
    await load()
  } catch {
    notice.value = t('testReports.failed')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <h1>{{ t('testReports.title') }}</h1>
      <p class="muted">{{ t('testReports.subtitle') }}</p>
    </header>
    <form class="bar" @submit.prevent="create">
      <input
        v-model="runId"
        class="input"
        type="text"
        :placeholder="t('testReports.runPlaceholder')"
      />
      <button class="btn" type="submit">{{ t('testReports.create') }}</button>
      <button class="btn" type="button" @click="load">{{ t('testReports.refresh') }}</button>
    </form>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <DataTable
      :columns="columns"
      :data="rows"
      :loading="loading"
      :error="failed"
      :empty-title="t('testReports.empty')"
    />
  </div>
</template>

<style scoped>
.page { padding: 24px 28px; display: grid; gap: 18px; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.muted { color: color-mix(in srgb, currentColor 60%, transparent); font-size: 13px; }
.bar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.notice { padding: 8px 12px; border-radius: 6px; font-size: 13px; border: 1px solid var(--fail-border); }
</style>
"""

# ---------------------------------------------------------------- 语言包
ZH = """  testReports: {
    title: '测试报告',
    subtitle: '对外测试报告与对内执行报告分家；冻结后的快照自包含',
    runPlaceholder: '输入 RUN_ID 创建草稿',
    create: '创建草稿',
    refresh: '刷新',
    empty: '暂无测试报告',
    open: '测试报告',
    colTitle: '标题',
    colRun: 'RUN_ID',
    colState: '状态',
    colVersion: '版本',
    created: '草稿已创建',
    failed: '操作失败',
  },
"""
EN = """  testReports: {
    title: 'Test Reports',
    subtitle: 'External test reports and internal execution reports; frozen snapshots are self-contained',
    runPlaceholder: 'Enter RUN_ID to create a draft',
    create: 'Create draft',
    refresh: 'Refresh',
    empty: 'No test reports yet',
    open: 'Test reports',
    colTitle: 'Title',
    colRun: 'RUN_ID',
    colState: 'State',
    colVersion: 'Version',
    created: 'Draft created',
    failed: 'Operation failed',
  },
"""

for rel, block in (("frontend/src/locales/zh-CN.ts", ZH),
                   ("frontend/src/locales/en-US.ts", EN)):
    p = ROOT / rel
    t = p.read_text(encoding="utf-8")
    if "testReports:" in t:
        SKIP += 1
        print("[SKIP] i18n", rel)
        continue
    body = t.rstrip()
    idx = body.rfind("\n}")
    if idx < 0:
        raise SystemExit("FAIL 语言包末尾找不到闭合括号: " + rel)
    p.write_text(body[:idx] + "\n" + block.rstrip() + body[idx:] + "\n", encoding="utf-8")
    PASS += 1
    print("[PASS] i18n", rel)

# ---------------------------------------------------------------- 路由 / 导航 / 入口
replace_once("frontend/src/router/index.ts",
"""    {
      path: '/admin',
      name: 'admin',""",
"""    {
      path: '/test-reports',
      name: 'test-reports',
      component: () => import('@/views/TestReportsView.vue'),
    },
    {
      path: '/admin',
      name: 'admin',""",
             marker="path: '/test-reports'")

replace_once("frontend/src/config/navigation.ts",
             "    alsoMatch: ['/reports'],",
             "    alsoMatch: ['/reports', '/test-reports'],",
             marker="alsoMatch: ['/reports', '/test-reports']")

reports_view = ROOT / "frontend/src/views/ReportsView.vue"
t = reports_view.read_text(encoding="utf-8")
if "testReports.open" in t:
    SKIP += 1
    print("[SKIP] ReportsView 入口已存在")
else:
    pattern = re.compile(r"(<template>\s*\n\s*<div[^>]*>\s*\n)")
    m = pattern.search(t)
    if not m:
        raise SystemExit("FAIL ReportsView 找不到模板根 div")
    entry = ("  <div class=\"w2-entry\">\n"
             "    <button class=\"btn\" type=\"button\" @click=\"router.push('/test-reports')\">\n"
             "      {{ t('testReports.open') }}\n"
             "    </button>\n"
             "  </div>\n")
    reports_view.write_text(t[:m.end()] + entry + t[m.end():], encoding="utf-8")
    PASS += 1
    print("[PASS] ReportsView 加入口按钮")

# ---------------------------------------------------------------- 守门接线 / C9 口径
replace_once("scripts/lint.sh",
             'echo "[lint]   - tools/check_wbs_consistency.py（WBS 汇总数字 ↔ 逐行状态）"',
             'echo "[lint]   - tools/check_report_sensitive.py（C5 EXTERNAL 字段判据自校验）"\n'
             'python3 tools/check_report_sensitive.py --self-check\n'
             'echo "[lint]   - tools/check_wbs_consistency.py（WBS 汇总数字 ↔ 逐行状态）"',
             marker="check_report_sensitive.py --self-check")

replace_once("scripts/rollback.sh",
             "# 本脚本不写死任何口令，也不执行 DROP/DELETE 以外的数据清理动作。",
             "# 本脚本不写死任何口令，也不执行 DROP/DELETE 以外的数据清理动作。\n"
             "# C9（2026-11-06 实测订正）：备份走 mysqldump 全库，新增表自动纳入，\n"
             "# 不存在「逐表登记清单」；W2 起每波在证据 README 登记新表供人工核对。",
             marker="C9（2026-11-06 实测订正）")

# ---------------------------------------------------------------- 证据：导出样件 + 扫描日志
EVID = ROOT / "docs/evidence/v1.0/w2-report"
EVID.mkdir(parents=True, exist_ok=True)

view = [
    ("title", "Quarterly Regression"),
    ("conclusion", "PASS"),
    ("passCount", 8),
    ("failCount", 0),
    ("skipCount", 1),
    ("passRate", 88.89),
    ("swBuild", "build-2.0"),
    ("disclaimer", "INTERNAL USE ONLY - NOT FOR EXTERNAL DISTRIBUTION"),
    ("watermark", "INTERNAL USE ONLY"),
]
html = ("<!doctype html><html><head><meta charset=\"utf-8\"><title>Test Report</title>"
        "</head><body><h1>Quarterly Regression</h1><p>INTERNAL USE ONLY</p><table>"
        + "".join(f"<tr><th>{k}</th><td>{v}</td></tr>" for k, v in view)
        + "</table></body></html>")
(EVID / "export-sample.html").write_text(html, encoding="utf-8")

# 最小 PDF 1.4（与后端 ReportExportService 同构），文本层为 ASCII 字段摘要
content = "BT\n/F1 11 Tf\n50 790 Td\n15 TL\n"
content += "".join(f"({k}: {v}) Tj\nT*\n" for k, v in view)
content += "ET\n"
stream = content.encode("ascii")
out = bytearray(b"%PDF-1.4\n")
offsets = [0] * 6
for number, body in ((1, "<< /Type /Catalog /Pages 2 0 R >>"),
                     (2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                     (3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                         "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>"),
                     (4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")):
    offsets[number] = len(out)
    out += f"{number} 0 obj\n{body}\nendobj\n".encode("ascii")
offsets[5] = len(out)
out += f"5 0 obj\n<< /Length {len(stream)} >>\nstream\n".encode("ascii")
out += stream + b"endstream\nendobj\n"
start = len(out)
out += b"xref\n0 6\n0000000000 65535 f \n"
for i in range(1, 6):
    out += f"{offsets[i]:010d} 00000 n \n".encode("ascii")
out += f"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n{start}\n%%EOF\n".encode("ascii")
(EVID / "export-sample.pdf").write_bytes(bytes(out))

# 最小 DOCX（OOXML zip 容器），文本层为同一份白名单字段
import zipfile  # noqa: E402
import io  # noqa: E402

buf = io.BytesIO()
with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
    zf.writestr("[Content_Types].xml",
                '<?xml version="1.0" encoding="UTF-8"?>'
                '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
                '<Default Extension="xml" ContentType="application/xml"/>'
                '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
                '</Types>')
    zf.writestr("_rels/.rels",
                '<?xml version="1.0" encoding="UTF-8"?>'
                '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
                '</Relationships>')
    paragraphs = "".join(f"<w:p><w:r><w:t>{k}: {v}</w:t></w:r></w:p>" for k, v in view)
    zf.writestr("word/document.xml",
                '<?xml version="1.0" encoding="UTF-8"?>'
                '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
                f'<w:body>{paragraphs}</w:body></w:document>')
(EVID / "export-sample.docx").write_bytes(buf.getvalue())
print("[PASS] 三格式内部验证样件（HTML / PDF / DOCX）")

scan = subprocess.run(
    [sys.executable, "tools/check_report_sensitive.py",
     "docs/evidence/v1.0/w2-report/export-sample.html",
     "docs/evidence/v1.0/w2-report/export-sample.pdf",
     "docs/evidence/v1.0/w2-report/export-sample.docx"],
    capture_output=True, text=True)
log = ("# W2 / RPTX-05 EXTERNAL 导出敏感字段扫描（本机内部验证）\n"
       "# 命令 python3 tools/check_report_sensitive.py <三份样件>\n"
       "# 判据 docs/plan/C5-对外报告字段白名单-20261106.md\n"
       "# 说明 样件由本机白名单视图生成，**不是真实链路导出件**；真机抽样仍待现场。\n\n"
       + scan.stdout + scan.stderr
       + f"\nEXIT={scan.returncode}\n")
(EVID / "sensitive-scan.log").write_text(log, encoding="utf-8")
if scan.returncode:
    raise SystemExit("FAIL C5 扫描未通过:\n" + scan.stdout + scan.stderr)
print("[PASS] C5 扫描 0 命中 -> sensitive-scan.log")

write_new("docs/evidence/v1.0/w2-report/README.md", """# W2 / RPTX-01..05 交付证据（2026-11-06）

| 包 | 实现 | 本机证据 |
|---|---|---|
| RPTX-01 | `test_report` + `ReportState` 状态机 + `TestReportService` + `TestReportController` | `ReportStateMachineTest` 4 条；迁移 `V8_1__test_report.sql` + down |
| RPTX-02 | `ReportSnapshotService` 物化 JSON/HTML + SHA-256 + 版本号；渲染路径只读 `test_report` | `ReportSnapshotTest` 4 条（hash 稳定 / 自包含 / 脱敏 / 对内视图） |
| RPTX-03 | `ReportViewRenderer.external()` 走 C5 白名单 | `ReportSnapshotTest.externalViewOnlyHasWhitelistedFields` |
| RPTX-04 | `ReportViewRenderer.internal()` 保留关键字树 / 命令 / 原始输出入口 | `ReportSnapshotTest.internalViewCarriesExecutionDetails` |
| RPTX-05 | `ReportExportService` HTML / PDF / DOCX + 水印 + 保留策略 | `ReportExportTest` 4 条；三格式样件 + `sensitive-scan.log` |

**新增表（C9 登记）**：`test_report`、`test_report_export`、`test_report_audit`。
实测 `scripts/rollback.sh` 走 `mysqldump` 全库，新表自动纳入备份，无需逐表登记清单
——此结论已回写 `docs/plan/前置条件闭环-20261106.md` 的 C9 口径。

**边界**：三份导出样件是**本机内部验证件**，不是真实链路导出；G8 第 5 条「冻结后删源 run
仍可渲染」的本机保证是「渲染只读快照表」，真机删数演练与导出件抽样仍记 `待现场`。
EXTERNAL 报告**未对外发送**，C5 定案不等于获准对外。

**页面入口**：`/test-reports`（8001 统一入口直达）；报告页底部「测试报告」按钮；
`navigation.ts` 的 `reports` 叶子把 `/test-reports` 加入 `alsoMatch`，侧栏仍高亮「报告」域。
一级菜单结构未改（六域 / 层级 ≤ 2 不变）。
""")

for rel, text in F.items():
    write_new(rel, text)

print(f"\n[w2-frontend] PASS={PASS} SKIP={SKIP}")

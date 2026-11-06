#!/usr/bin/env python3
"""W1 六包 + P1 五包 + WS-02 的 AI 验收回写（幂等）。

授权：大师 2026-11-06 15:23「都依照你推荐的为准，全部完成后再找我人工复核」
+ WBS「AI 验收放行」口径。只改状态/证据/阻塞三列与汇总五数，
不改 FR / TC / 门禁判据，也不碰 7 个 `待现场` 包。
"""
from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[4]
WBS = ROOT / "docs" / "plan" / "WBS-STATUS.md"
SPLIT = re.compile(r"(?<!\\)\|")

# WBS 编号 → (证据, 验收依据)
CLOSE = {
    "ENVX-01": ("`scripts/serve-entry.mjs`、`scripts/verify-entry.sh`（12/12 PASS）",
                "8001 静态+SPA fallback+缓存+反代实测全绿；深链接 /settings 200"),
    "ENVX-02": ("`scripts/deploy.sh`、`docs/ops/端口矩阵.md`、`deploy/` 样例",
                "/healthz 与 /api/health 分离实测通过；部署/回滚/TLS 样例齐"),
    "NAV-01": ("`frontend/src/config/navigation.ts`、`navigation.spec.ts`（4 角色快照）",
               "6 域 IA + 能力过滤 + 项目选择器上移 + 403 路由守卫，前端 220/220"),
    "NAV-02": ("`SystemSettingService` + `SystemSettingServiceTest`（5 条）",
               "平台/项目分级、白名单校验、变更写审计；后端 642/642"),
    "NAV-03": ("`BuildInfoService`、`AboutView.vue`、`ChangelogView.vue`、`CHANGELOG.md`",
               "构建信息来自 build-info.properties；依赖 23 项；自检四项；CHANGELOG 四类"),
    "NAV-04": ("`CommandPalette.vue`、`commandPalette.spec.ts`（8 条）",
               "Ctrl+K 中英文搜索 / 最近 5 条 / 收藏 / 折叠持久化，单测通过"),
    "AST-04": ("`db/migration/V7_8__domain_tag_backfill.sql`、`docs/evidence/v1.0/p1-closeout/`",
               "案 A-②：按 case_kind 映射打标，226 条 domain_tag 空值实测归零"),
    "CAS-03": ("`docs/evidence/v0.2/G2-核验纪要-20261104.md` §1 + 本批定案",
               "案 B：226 为验收口径、461 记历史全集待迁移，导入实现一行不改"),
    "UX-04": ("`docs/plan/P1-产品口径两案清单-20261106.md` §3 + 本批定案",
              "案 B：承认 11-05「免评审」裁决即结论，不补形式签字"),
    "EXEC-01": ("`docs/plan/P1-产品口径两案清单-20261106.md` §4 + 本批定案",
                "案 B：两处口径偏差写进定案，不改动已冻结实现"),
    "PRJ-01": ("`db/migration/V7_7__project_owner_backfill.sql`、`docs/evidence/v1.0/p1-closeout/`",
               "案 A：通用幂等回填进 Flyway；owner_id 空值 0、旧 owner 角色 0，47 表不变"),
    "WS-02": ("`docs/evidence/v1.0/ws02-checkstyle/README.md`",
              "Checkstyle 3.6.0 真实阻断：0 violations；注入星号导入 → EXPECTED_FAIL 元验证"),
}

STAMP = "**AI 验收 2026-11-06**"


def split_cells(line: str) -> list[str]:
    parts = SPLIT.split(line.strip())
    return [part.strip() for part in parts[1:-1]]


def join_cells(cells: list[str]) -> str:
    return "| " + " | ".join(cells) + " |"


def main() -> int:
    lines = WBS.read_text(encoding="utf-8").splitlines()
    changed: list[str] = []
    counts: dict[str, int] = {
        "未开始": 0, "进行中": 0, "待验": 0, "待现场": 0, "已完成": 0,
    }

    for index, line in enumerate(lines):
        if not re.match(r"^\| \d+ \| ", line):
            continue
        cells = split_cells(line)
        if len(cells) != 7:
            continue
        code_match = re.search(r"[A-Z]+-?[A-Z]*\d*-[0-9]+|[A-Z]+-\d+", cells[1])
        code = code_match.group(0) if code_match else cells[1].replace("*", "")
        if code in CLOSE and cells[4] != "已完成":
            evidence, reason = CLOSE[code]
            cells[4] = "已完成"
            cells[5] = evidence
            cells[6] = f"{reason}｜{STAMP}"
            lines[index] = join_cells(cells)
            changed.append(code)
        if cells[4] in counts:
            counts[cells[4]] += 1

    text = "\n".join(lines) + "\n"
    total = sum(counts.values())
    done = counts["已完成"]
    pct = round(done * 100 / total, 1)

    summary_old = re.search(r"^\| \d+ 个任务包 \|.*\|$", text, re.M)
    if summary_old:
        text = text.replace(
            summary_old.group(0),
            f"| {total} 个任务包 | **{counts['未开始']}** | **{counts['进行中']}** | "
            f"**{counts['待验']}** | **{counts['待现场']}** | **{done}** |",
            1,
        )
    progress_old = re.search(r"^\*\*当前进度：.*\*\*$", text, re.M)
    if progress_old:
        text = text.replace(
            progress_old.group(0),
            f"**当前进度：{done} / {total}（约 {pct}% · {counts['待验']} 待验 · "
            f"{counts['待现场']} 待现场 · {counts['未开始']} 未开始 · {counts['进行中']} 进行中）**",
            1,
        )
    # 分组表三行同步（只改包数与现状里的旧数字）
    text = text.replace("| **已完成** | 75 |", f"| **已完成** | {done} |", 1)
    text = text.replace("| **待验**（有真实技术缺口） | 14 |", f"| **待验**（有真实技术缺口） | {counts['待验']} |", 1)
    text = text.replace("| **进行中**（v2.0 W1 六包） | 6 |", f"| **进行中** | {counts['进行中']} |", 1)

    WBS.write_text(text, encoding="utf-8")
    print(f"[closeout] 转已完成 {len(changed)} 包：{', '.join(changed)}")
    print(f"[closeout] 五数：{counts} 合计 {total}，进度 {done}/{total} = {pct}%")
    if len(changed) != len(CLOSE):
        print(f"[closeout] 注意：期望收口 {len(CLOSE)} 包，本次新转 {len(changed)}（重跑时为 0 属正常）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""收口留痕：两案清单勾定、门禁纪要复核、决策纪要 §10、WBS 补记（幂等）。"""
from __future__ import annotations

import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[4]


def append_once(rel: str, marker: str, block: str, label: str) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if marker in text:
        print(f"SKIP  {rel} :: {label}")
        return
    path.write_text(text.rstrip("\n") + "\n\n" + block.strip("\n") + "\n", encoding="utf-8")
    print(f"PASS  {rel} :: {label}")


def replace_once(rel: str, old: str, new: str, label: str, expect: int = 1) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if old not in text:
        if new in text:
            print(f"SKIP  {rel} :: {label}")
            return
        raise SystemExit(f"FAIL {rel} :: {label} :: 未命中")
    n = text.count(old)
    if n != expect:
        raise SystemExit(f"FAIL {rel} :: {label} :: 命中 {n} 次，期望 {expect}")
    path.write_text(text.replace(old, new, expect), encoding="utf-8")
    print(f"PASS  {rel} :: {label}（{expect} 处）")


# ---- 1. 两案清单：勾定栏从「待勾」改成「按推荐值执行」 ----
CASES = "docs/plan/P1-产品口径两案清单-20261106.md"
text = (ROOT / CASES).read_text(encoding="utf-8")
if "☑ 推荐值" not in text:
    rows = text.splitlines()
    for i, line in enumerate(rows):
        if line.startswith("| AST-04 |") or line.startswith("| CAS-03 |") \
                or line.startswith("| UX-04 |") or line.startswith("| EXEC-01 |") \
                or line.startswith("| PRJ-01 |"):
            rows[i] = line.replace("☐ A　☐ B　☐ 其他", "☑ 推荐值（AI 按授权执行）")
    (ROOT / CASES).write_text("\n".join(rows) + "\n", encoding="utf-8")
    print("PASS  两案清单 5 个勾定栏落地")
else:
    print("SKIP  两案清单勾定栏")

append_once(
    CASES,
    "## 9. 2026-11-06 15:23 一次性授权下的执行结果",
    """## 9. 2026-11-06 15:23 一次性授权下的执行结果

大师指令「期间不要停下来问我，都依照你推荐的为准，全部完成后再找我人工复核」，
据此 AI 按 §6 推荐值逐项执行，**不再逐项等勾定**；授权与边界见
`docs/plan/决策纪要-20261106.md` §9。

| 包 | 定案 | 实际落地 | 验证读数 |
|---|---|---|---|
| AST-04 | 案 A-② 一级分类映射 | `V7_8__domain_tag_backfill.sql`（13 类 + 兜底，只填空值、不覆盖 roce/storage） | 226 条 `domain_tag` 空值 **归零**；分布 ethernet 165 / rdma 34 / qos 12 / roce 10 / storage 5 |
| CAS-03 | 案 B | 沿用 G2 初稿 §1 裁决：226 为验收口径，461 记历史全集待迁移 | 导入实现 0 改动；4 列命令丢失定案不变 |
| UX-04 | 案 B | 承认 11-05「免评审」裁决即结论 | 不补形式签字 |
| EXEC-01 | 案 B | 两处口径偏差写进定案 | 已冻结实现 0 改动 |
| PRJ-01 | 案 A（授权新增 Flyway） | `V7_7__project_owner_backfill.sql`（通用幂等，不写死 id） | `owner_id` 空值 0、旧 owner 角色 0、业务表仍 47 |
| WS-02 | — | 上一批 Checkstyle 真实接入一并验收 | 0 violations + 注入违规元验证 |

迁移经 `scripts/db-migrate.sh` 在 WSL 开发库实跑通过；静态守门
`backend/src/test/java/com/qa/autotest/migration/V7BackfillTest.java` 随 `mvn test` 执行。
五包 + W1 六包 + WS-02 共 **12 包** 已按 AI 验收口径回写 `WBS-STATUS.md` 并留
`AI 验收 2026-11-06` 戳，五数变为 **87 已完成 / 8 待验 / 7 待现场 / 28 未开始 / 0 进行中（130）**。

**人工复核提示**：AST-04 的「一级分类 → 技术域」映射是 AI 按语义制定（属推断，不是台账事实），
若大师有正式映射表，改 `V7_8` 的 CASE 并重跑迁移即可；已有人工标注不会被覆盖。
""",
    "两案清单追加 §9 执行结果",
)

# ---- 2. 门禁核验纪要：补 W1 后的复核 ----
append_once(
    "docs/plan/门禁核验纪要-G2-G5-20261106.md",
    "## 附：2026-11-06 W1 完成后的复核",
    """## 附：2026-11-06 W1 完成后的复核

W1 六包已完成并留证，但**不改变任何门禁判定**：

| 门 | 原判定 | 本轮复核 |
|---|---|---|
| G1 | 11-04 判过（带偏差） | 不变；七版 `05-TEST-REPORT.md` 已补齐（流程第 ⑥ 道） |
| G2 | 11-04 核验纪要原文「本门未判过」 | 不变；6 项仍待现场 |
| G3 | 无书面核验纪要 | 不变 |
| G4 | 无书面核验纪要 | 不变 → **W3 不得开工** |
| G5 | 无书面核验纪要 | 不变 → **W2 不得开工** |

新增证据：`scripts/verify-entry.sh` 12/12 PASS（8001 探针 / SPA / 缓存 / 反代）、
后端 642/642、前端 220/220、Checkstyle 0 violations、`db-audit` 8 项 / 47 表。
七版测试报告统一口径：**本轮重跑的是机器门，逐条 TC 标 `EVIDENCE-ONLY`，真机项 `待现场`**。
""",
    "门禁纪要追加 W1 复核",
)

# ---- 3. 决策纪要 §10 ----
append_once(
    "docs/plan/决策纪要-20261106.md",
    "## 10. 本轮开发完成与人工复核清单",
    """## 10. 本轮开发完成与人工复核清单（W1 收口）

按 §9 授权，本轮完成 v2.0 **W1 六包** 的真实开发（8001 统一入口、6 域 IA + 能力过滤、
系统设置分级、关于/版本迭代页、命令面板、健康检查与部署脚本），并收口 P1 五包 + WS-02。
详见 `docs/plan/本轮开发完成报告-20261106.md` 与 `docs/evidence/v1.0/w1-kickoff/`、
`docs/evidence/v1.0/p1-closeout/`、`docs/evidence/v1.0/w1-delivery/`。

**未转移的边界**：发版 / 打 tag / 破坏性变更 / 对外口径仍归大师；`G-授权`（真机、外部凭据）
与 ADR-0013 禁跑令仍归大师；G4/G5 未过 → W2/W3 不得开工；C2/C3/C5 真实样本未到位 →
W4/W6 样本类 TC 记 `NOT-RUN`；7 个 `待现场` 包不自评通过。

**人工复核四问**：① AST-04 映射规则是否采纳；② W1 六包是否验收放行；
③ G2 的 L0 签字与 G4/G5 核验安排；④ C2/C3/C5 三样实物何时补齐以解锁 W2/W3/W4/W6。
""",
    "决策纪要追加 §10",
)

# ---- 4. WBS 头部补记（追加型替换必须带 marker，否则重跑会重复插入） ----
WBS = ROOT / "docs/plan/WBS-STATUS.md"
WBS_HEAD = "## 2026-11-06 第二次 AI 验收（W1 收口）"
_wbs_text = WBS.read_text(encoding="utf-8")
# 先清理早期版本误插的重复块，保证最终只有一份
while _wbs_text.count(WBS_HEAD) > 1:
    first = _wbs_text.index(WBS_HEAD)
    second = _wbs_text.index(WBS_HEAD, first + 1)
    end = _wbs_text.index("## 更新方式", second)
    _wbs_text = _wbs_text[:second] + _wbs_text[end:]
if _wbs_text != WBS.read_text(encoding="utf-8"):
    WBS.write_text(_wbs_text, encoding="utf-8")
    print("PASS  WBS 清理重复补记块")

if WBS_HEAD in WBS.read_text(encoding="utf-8"):
    print("SKIP  WBS 头部补记（已存在）")
else:
    replace_once(
        "docs/plan/WBS-STATUS.md",
        "## 更新方式",
        """## 2026-11-06 第二次 AI 验收（W1 收口）

本轮按大师 15:23 一次性授权完成 W1 六包开发、P1 五包按推荐值定案、WS-02 Checkstyle 验收，
共 **12 包** 转 `已完成` 并留 `AI 验收 2026-11-06` 戳；新增 `V7_7` / `V7_8` 两条幂等数据回填迁移，
七版 `05-TEST-REPORT.md` 补齐。真机 / 外部样本 / 门禁判定一项未冒进：
**7 个 `待现场` 与 8 个 `待验` 保持原状**，W2–W7 因 G4/G5 与 C2/C3/C5 未满足而不开工。
证据见 `docs/evidence/v1.0/w1-delivery/README.md`。

---

## 更新方式""",
        "WBS 头部补第二次 AI 验收说明",
    )

print("DONE  finalize 完成")

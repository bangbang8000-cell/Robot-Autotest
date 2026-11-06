#!/usr/bin/env python3
"""v2.0 ⑥ 测试报告：计划态快照 → W1 分层实测台账（七道流程第 ⑦ 道回写）。

诚实口径（不把单测说成 E2E、不把 W1 说成 v2.0 通过）：
  PASS    本轮有直接机器证据（守门 / 单测 / 入口实测脚本）
  PARTIAL 单测或纯逻辑通过，但真实页面 / 实库写入 / WS 握手 / 键盘 E2E 未跑
  NOT-RUN 前置未满足或演练未做（含 TC-67 回滚、W2–W7 的 56 条）
G8 未判过；W2–W7 未开工。67 条 TC 全部保留台账行，机检判据 3 仍成立。
"""
from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[4]
REPORT = ROOT / "docs/release/v2.0-platform/05-TEST-REPORT.md"

# TC 尾号 → (结果, 证据, 备注)
W1: dict[int, tuple[str, str, str]] = {
    10: ("PASS", "`config/navigation.spec.ts`", "6 域 / 层级 ≤2 / 能力过滤，单测断言"),
    11: ("PARTIAL", "`navigation.spec.ts` + `ForbiddenView` + 路由守卫", "4 角色覆盖；直达无权限路由 E2E 未跑"),
    12: ("PARTIAL", "`config/commandPalette.spec.ts`", "搜索/最近 5/收藏逻辑通过；键盘 E2E 未跑"),
    13: ("PARTIAL", "`SystemSettingServiceTest`", "默认值/403/非法值/审计通过；实库写入未跑"),
    14: ("PARTIAL", "`SystemSettingServiceTest`", "项目/平台隔离通过；页面回显未跑"),
    15: ("PARTIAL", "`BuildInfoServiceTest` + `/api/system/build-info`", "端点存在（verify-entry）；页面实渲未跑"),
    16: ("PASS", "`config/changelog.spec.ts` + verify-entry `/CHANGELOG.md` 200", "四类齐全/当前唯一/倒序，静态数据可达"),
    17: ("PARTIAL", "`config/commandPalette.spec.ts`", "折叠/收藏持久化通过；切项目 UI 未跑"),
    65: ("PASS", "`scripts/verify-entry.sh` 12/12", "首页/深链接/404/缓存头均实测"),
    66: ("PARTIAL", "`scripts/verify-entry.sh` 双探针", "`/healthz`、`/api/health` PASS；WS 101 未实测"),
    67: ("NOT-RUN", "`scripts/deploy.sh` 已提供", "部署→回滚→再检查演练未跑"),
}

HEADER_OLD = """# v2.0 平台化 · ⑥ 测试报告（计划态快照）

**文档编号** RA-REL-2026-020-TR ｜ **版本** v2.0-platform ｜ **日期** 2026-11-06 ｜ **状态** 未开工
**说明** 本版尚未开始编码与执行，本文件仅用于保证版本流程产物齐全，并给出可核对的执行台账。**任何条目都不得被解读为已通过。**"""

HEADER_NEW = """# v2.0 平台化 · ⑥ 测试报告（W1 分层实测台账）

**文档编号** RA-REL-2026-020-TR ｜ **版本** v2.0-platform ｜ **日期** 2026-11-06
**状态** W1 六包已实现并本机实测；**W2–W7 未开工、G8 未判过**
**说明** 本文件在 2026-11-06 W1 收口时由「计划态快照」改写。结果分三档：
`PASS` = 本轮有直接机器证据；`PARTIAL` = 单测/纯逻辑通过但真实页面、实库写入、WS 握手或键盘 E2E 未跑；
`NOT-RUN` = 前置未满足或演练未做。**PARTIAL 不得读作通过，W1 结果不得读作 v2.0 通过。**"""

CONCLUSION_OLD = """| 开工日期 | — |
| 已完成 TC | 0 / 67 |
| 通过 | 0 |
| 失败 | 0 |
| 未执行（NOT-RUN） | 67 |
| 当前结论 | 计划态，无执行证据 |"""

CONCLUSION_NEW = """| 开工日期 | 2026-11-06 |
| W1 相关 TC | 11 条（TC-v2.0-10~17、65~67） |
| PASS | **3**（TC-v2.0-10 / 16 / 65） |
| PARTIAL | **7**（单测或逻辑通过，E2E / 实库 / WS 未跑） |
| 失败 | 0 |
| NOT-RUN | **57**（TC-v2.0-67 回滚演练 1 条 + W2–W7 的 56 条） |
| 本轮机器门 | 八步门禁 `FAILS=0`；入口实测 `12/12`；版本机检 `FAIL 0` |
| 当前结论 | **W1 机器门全绿；v2.0 未通过 G8，W2–W7 未开工** |"""

SECTION3_OLD = """- 本文件在每次 Wave 完成后由负责人更新对应行（结果 / 证据位置 / 备注），禁止事后补写「通过」。
- 「证据位置」填 `docs/evidence/v2.0/<包>/README.md` 的锚点或具体命令输出文件。
- 若某条 TC 因前置条件缺失无法执行，结果写 NOT-RUN，备注写缺失条件与补测计划，不写「跳过」。"""

SECTION3_NEW = """- 本文件在每次 Wave 完成后更新对应行（结果 / 证据位置 / 备注），**禁止事后补写「通过」**。
- 「证据位置」填 `docs/evidence/v2.0/<包>/README.md` 的锚点或具体命令输出文件。
- 若某条 TC 因前置条件缺失无法执行，结果写 NOT-RUN，备注写缺失条件与补测计划，不写「跳过」。
- **W1 证据的边界**：本轮没有跑浏览器 E2E、没有带登录态打实库写入、没有做 WebSocket 101 握手、
  没有做部署回滚演练。相应 TC 一律 `PARTIAL` / `NOT-RUN`，不因单测通过而升格。
- **G8 十条判据未核验**：报告双实体（G5）、双内核（G4）、C2/C3/C5 三样外部样本仍缺，
  W2/W3 不得开工；`G-授权` 与 ADR-0013 禁跑令未解除。见 `docs/plan/门禁核验纪要-G2-G5-20261106.md`。

## 4. 本轮机器门实测（2026-11-06）

| 门 | 命令 | 结果 |
|---|---|---|
| 八步门禁 | `bash docs/evidence/v1.0/policy-20261106/gates.sh` | 10 步全 PASS，`GATES_RESULT FAILS=0` |
| 统一入口实测 | `bash scripts/verify-entry.sh` | 12 / 12 PASS |
| 版本流程机检 | `python3 tools/check_release_docs.py` | FAIL 0 项（v2.0 PASS） |
| 后端单测 | `mvn -B test` | 通过（含 W1 新增 3 个测试类 + V7 回填守门） |
| 前端单测 | `npm run test` | 通过（含 navigation / commandPalette / changelog 三套新用例） |
| 数据回填 | `bash scripts/db-migrate.sh` | V7_7 / V7_8 应用后 3 项断言均为 0 |

> 门禁原始日志落在 `docs/evidence/v1.0/policy-20261106/logs/`（被 `.gitignore` 忽略，可由 gates.sh 复跑重建）。
> 逐项修复与证据见 `docs/evidence/v1.0/w1-delivery/README.md`。"""

REV_OLD = """| 2026-11-06 | v1.0 | 初版：67 条 TC 计划态台账，全部 NOT-RUN | 版本流程要求六份产物齐全，本版尚未开工 |"""

REV_NEW = """| 2026-11-06 | v1.0 | 初版：67 条 TC 计划态台账，全部 NOT-RUN | 版本流程要求六份产物齐全，本版尚未开工 |
| 2026-11-06 | v1.1 | **推翻 v1.0 的「未开工 / 全部 NOT-RUN」**：W1 六包已实现并本机实测，11 条 W1 TC 拆为 PASS 3 / PARTIAL 7 / NOT-RUN 1；新增 §4 机器门实测；标题与状态行同步 | 七道流程第 ⑦ 道文档回写；按「被实测推翻的假设逐条改掉并留修订记录」办理，不悄悄改数字 |"""


def main() -> int:
    text = REPORT.read_text(encoding="utf-8")
    if "W1 分层实测台账" in text:
        print("SKIP  v2.0 测试报告已回写")
        return 0

    for label, old, new in [
        ("头部状态", HEADER_OLD, HEADER_NEW),
        ("结论表", CONCLUSION_OLD, CONCLUSION_NEW),
        ("§3 说明 + 新增 §4", SECTION3_OLD, SECTION3_NEW),
        ("修订记录 v1.1", REV_OLD, REV_NEW),
    ]:
        hits = text.count(old)
        if hits != 1:
            raise SystemExit(f"FAIL  {label}：期望命中 1 次，实际 {hits}")
        text = text.replace(old, new, 1)
        print(f"PASS  {label}")

    # 台账：W1 的 11 行按证据改写，其余 56 行保持 NOT-RUN
    row_re = re.compile(r"^\| TC-v2\.0-(\d+) \| (FR-v2\.0-\d+) \| NOT-RUN \| — \| (待 W\d) \|$", re.M)
    changed: list[int] = []

    def rewrite(match: re.Match) -> str:
        num = int(match.group(1))
        if num not in W1:
            return match.group(0)
        result, evidence, note = W1[num]
        changed.append(num)
        return f"| TC-v2.0-{num:02d} | {match.group(2)} | {result} | {evidence} | {note} |"

    text, n = row_re.subn(rewrite, text)
    if sorted(changed) != sorted(W1):
        raise SystemExit(f"FAIL  台账改写不完整：{sorted(changed)} != {sorted(W1)}")
    print(f"PASS  台账：扫描 {n} 行，改写 {len(changed)} 行 W1 TC")

    REPORT.write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

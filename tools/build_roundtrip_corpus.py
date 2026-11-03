#!/usr/bin/env python3
"""生成 EDT-06 往返保真语料的「legacy」与「syntax」两部分（确定性、可复现）。

`prod` 部分是 149 上真实套件的原样拷贝（由 `scripts/fetch_roundtrip_prod.sh` 拉取），
本脚本只负责另外两部分：

  legacy/  —— 从 8001 台账（`详细测试流程` 工作表）抽取 17 条，编译成 `.robot`。
             台账是「原表」的唯一真源，抽取清单见 LEGACY_IDS（写死，保证可复现）。
  syntax/  —— 手工构造 20 条语法边界样本，覆盖 DEV-PLAN §5 的必覆盖清单。

用法：
    python tools/build_roundtrip_corpus.py --xlsx <8001 台账 xlsx> --out fixtures/roundtrip

设计约束：
  1. 生成的 `.robot` 只能用 BuiltIn 里的关键字 —— `robot --dryrun` 会校验关键字存在，
     编造一个不存在的关键字会让 L1 直接红。
  2. 台账里的命令原文照抄，但折叠换行/多空格（RF 的 2 空格是单元格分隔符），
     并截断到 180 字符，避免把整段 shell 塞进一行。
  3. 不写账号口令；台账里出现 `--user admin` 这类不含口令的片段可以保留。
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

import openpyxl

# --- legacy 抽取清单（17 条，覆盖不同技术域）------------------------------------
LEGACY_IDS = [
    "TN-IMP-014",  # OS/网卡信息检查
    "TN-IMP-020",  # LLDP
    "TN-IMP-025",  # 光模块 DDM
    "TN-IMP-057",  # GRO 抓包（A+B）
    "TN-IMP-069",  # TSO 抓包（A+B）
    "TN-IMP-089",  # 硬件校验和卸载
    "TN-IMP-095",  # RSS 流量分发
    "TN-IMP-096",  # 中断聚合
    "TN-IMP-101",  # 多队列收发包
    "TN-IMP-118",  # ip link UP/DOWN 循环
    "TN-IMP-119",  # 大文件传输
    "TN-IMP-123",  # MTU9000 收发包
    "TN-IMP-136",  # VLAN 连通性
    "TN-IMP-142",  # VXLAN checksum offload
    "TN-IMP-159",  # RDMA PFC 优先级流控
    "TN-IMP-167",  # iperf 单口单向 TCP
    "TN-IMP-176",  # perftest 读延时
]

_WS = re.compile(r"\s+")


def _cell(value) -> str:
    """把单元格压成一行可安全放进 RF 单元格的文本。"""
    text = "" if value is None else str(value)
    text = text.replace("\u00a0", " ")
    return _WS.sub(" ", text).strip()


def _arg(value: str, limit: int = 180) -> str:
    """命令行参数：折叠空白后再截断；`#` 开头要转义，否则 RF 当注释。"""
    text = _cell(value)[:limit]
    if not text:
        return ""
    if text.startswith("#"):
        text = "\\" + text
    return text


def _doc_line(value: str, limit: int = 120) -> str:
    """Documentation 单行：去掉可能被 RF 当成续行标记的前导 `...`。"""
    text = _cell(value)[:limit]
    while text.startswith("..."):
        text = text[3:].strip()
    return text


LEGACY_TEMPLATE = """*** Settings ***
Documentation     8001 台账抽取：{cid} {case}
...               技术域：{domain}
Force Tags        legacy    {slug}
Test Timeout      30 minutes

*** Test Cases ***
{cid} {case}
    [Documentation]    {doc}
    [Tags]    {cid}    legacy    {slug}
    ${{A_CMD}}=    Set Variable    {a_cmd}
    Log    ${{A_CMD}}
    Log Many    A 端命令原文    {a_cmd}
    ...    B 端命令原文    {b_cmd}
    IF    "${{A_CMD}}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
"""


def build_legacy(xlsx: Path, out: Path) -> list[str]:
    wb = openpyxl.load_workbook(xlsx, read_only=True, data_only=True)
    sheet = wb["详细测试流程"]
    rows = {}
    for row in sheet.iter_rows(min_row=4, values_only=True):
        if row[0]:
            rows[str(row[0]).strip()] = row

    made = []
    out.mkdir(parents=True, exist_ok=True)
    for idx, cid in enumerate(LEGACY_IDS, 1):
        if cid not in rows:
            raise SystemExit(f"台账里找不到 {cid}，拒绝生成（否则语料不可追溯）")
        row = rows[cid]
        case = _doc_line(row[3], 60) or cid
        domain = _doc_line(row[2], 24) or "未分类"
        doc = _doc_line(row[10], 120) or "（台账未给出预期结果）"
        slug = f"legacy-{idx:02d}"
        text = LEGACY_TEMPLATE.format(
            cid=cid,
            case=case,
            domain=domain,
            slug=slug,
            doc=doc,
            a_cmd=_arg(row[7]),
            b_cmd=_arg(row[8]) or "（无）",
        )
        path = out / f"{slug}_{cid}.robot"
        path.write_text(text, encoding="utf-8")
        made.append(path.name)
    return made


# --- syntax 20 条：DEV-PLAN §5 必覆盖清单 --------------------------------------
SYNTAX_SAMPLES: list[tuple[str, str]] = [
    (
        "01_for_in_enumerate",
        """*** Settings ***
Documentation     语法边界 01：FOR + IN ENUMERATE，且块内再嵌一层 FOR
*** Variables ***
@{ITEMS}          alpha    beta    gamma
*** Test Cases ***
For In Enumerate Nested
    FOR    ${i}    ${item}    IN ENUMERATE    @{ITEMS}
        Log    第 ${i} 个：${item}
        FOR    ${j}    IN RANGE    1    3
            Log    内层 ${j}
        END
    END
""",
    ),
    (
        "02_if_else_if_else",
        """*** Settings ***
Documentation     语法边界 02：IF / ELSE IF / ELSE 三层分支
*** Test Cases ***
If Else If Else
    ${rc}=    Set Variable    ${0}
    IF    ${rc} > 0
        Log    正
    ELSE IF    ${rc} == 0
        Log    零
    ELSE
        Log    负
    END
""",
    ),
    (
        "03_try_except_finally",
        """*** Settings ***
Documentation     语法边界 03：TRY / EXCEPT / FINALLY
*** Test Cases ***
Try Except Finally
    TRY
        Fail    故意失败
    EXCEPT    *故意失败*    AS    ${err}
        Log    捕获：${err}
    FINALLY
        Log    清理完成
    END
""",
    ),
    (
        "04_while_limit",
        """*** Settings ***
Documentation     语法边界 04：WHILE 循环 + limit=
*** Test Cases ***
While With Limit
    ${i}=    Set Variable    ${0}
    WHILE    ${i} < 3    limit=10
        ${i}=    Evaluate    ${i} + 1
        Log    轮次 ${i}
    END
""",
    ),
    (
        "05_nested_variable",
        """*** Settings ***
Documentation     语法边界 05：内嵌变量 ${var_${i}} 与列表索引
*** Variables ***
${i}              1
${var_1}          值一
@{LIST}           a    b    c
*** Test Cases ***
Nested Variable
    Log    ${var_${i}}
    Log    ${LIST}[0]
    Log    ${var_${i}}_后缀
""",
    ),
    (
        "06_escaped_variable",
        """*** Settings ***
Documentation     语法边界 06：转义 \\${not_a_var} 必须保持字面量
*** Test Cases ***
Escaped Variable
    Log    \\${not_a_var} 是字面量
    Log    \\@{not_a_list}
    Log    \\%{NOT_AN_ENV}
""",
    ),
    (
        "07_continuation",
        """*** Settings ***
Documentation     语法边界 07：续行 ... 跨行参数
*** Test Cases ***
Continuation
    Log Many    one
    ...    two
    ...    three
    [Documentation]    第一行
    ...    第二行
    ...    第三行
    Log    done
""",
    ),
    (
        "08_template_table",
        """*** Settings ***
Documentation     语法边界 08：[Template] + 数据驱动多行
*** Test Cases ***
Template Driven
    [Template]    Log
    row one
    row two
    row three
""",
    ),
    (
        "09_setup_teardown_timeout",
        """*** Settings ***
Test Timeout      2 minutes
*** Test Cases ***
Setup Teardown Timeout
    [Setup]    Log    开始
    [Teardown]    Log    结束
    [Timeout]    30 seconds
    Log    主体
""",
    ),
    (
        "10_chinese_keywords",
        """*** Settings ***
Documentation     语法边界 10：中文关键字与中文参数值
*** Test Cases ***
中文用例
    [Tags]    中文    编码
    打印信息    中文参数值，含全角标点
    ${结果}=    拼接    α    β
    Log    ${结果}
*** Keywords ***
打印信息
    [Arguments]    ${文本}
    Log    ${文本}
拼接
    [Arguments]    ${左}    ${右}
    ${x}=    Catenate    ${左}${右}
    RETURN    ${x}
""",
    ),
    (
        "11_comments_dense",
        """*** Settings ***
Documentation     语法边界 11：空行与注释密集
# 顶部注释一
# 顶部注释二

*** Test Cases ***
注释密集
    # 用例内注释
    Log    a    # 行尾注释

    # 空行之后的注释
    Log    b
# 段末注释
""",
    ),
    (
        "12_variables_list_dict",
        """*** Settings ***
Documentation     语法边界 12：变量段含列表与字典
*** Variables ***
${SCALAR}         hello
@{LIST}           one    two    three
&{DICT}           k1=v1    k2=v2
${NESTED}         ${SCALAR}-suffix
*** Test Cases ***
Variables Shapes
    Log Many    @{LIST}
    Log    &{DICT}[k1]
""",
    ),
    (
        "13_library_with_name",
        """*** Settings ***
Documentation     语法边界 13：Library 带 WITH NAME 与导入参数
Library           Collections    WITH NAME    Coll
Library           String    WITH NAME    Str    # 行尾注释
*** Test Cases ***
Library Import
    ${l}=    Create List    a    b
    Log    ${l}
""",
    ),
    (
        "14_resource_import",
        """*** Settings ***
Documentation     语法边界 14：Resource 相对路径导入
Resource          syntax_helper.resource
*** Test Cases ***
Resource Import
    辅助打印    from resource
""",
    ),
    (
        "15_old_style_run_keyword_if",
        """*** Settings ***
Documentation     语法边界 15：旧式 Run Keyword If 单行写法
*** Test Cases ***
Old Style Run Keyword If
    ${cond}=    Set Variable    ${True}
    Run Keyword If    ${cond}    Log    yes
    ...    ELSE    Log    no
    Run Keyword If    ${cond}    Log    with else
    ...    ELSE IF    ${cond} == ${False}    Log    elif
    ...    ELSE    Log    else
""",
    ),
    (
        "16_very_long_line",
        """*** Settings ***
Documentation     语法边界 16：超过 500 字符的 JSON 参数不得被折叠
*** Variables ***
${JSON}           {"k": "LONGLONGPLACEHOLDER"}
*** Test Cases ***
Very Long Line
    Log    ${JSON}
""",
    ),
    (
        "17_tab_and_space",
        "*** Settings ***\nDocumentation\t语法边界 17：Tab 与空格混用\n*** Test Cases ***\nTab Mixed\n\tLog\ta\n    Log    b\n\tLog\tc\n",
    ),
    (
        "18_no_trailing_newline",
        """*** Settings ***
Documentation     语法边界 18：文件末尾无换行
*** Test Cases ***
No Trailing Newline
    Log    end""",
    ),
    (
        "19_crlf",
        "*** Settings ***\r\nDocumentation     语法边界 19：CRLF 换行\r\n*** Test Cases ***\r\nCRLF File\r\n    Log    crlf\r\n    Log    second\r\n",
    ),
    (
        "20_emoji_fullwidth",
        """*** Settings ***
Documentation     语法边界 20：emoji 与全角标点
*** Test Cases ***
Emoji And Fullwidth
    Log    测试完成 ✅ — 全角标点：，。！
    Log    🚀🔥 emoji与全角混排，单空格分隔参数
""",
    ),
]

SYNTAX_HELPER = """*** Keywords ***
辅助打印
    [Arguments]    ${文本}
    Log    ${文本}
"""


def build_syntax(out: Path) -> list[str]:
    out.mkdir(parents=True, exist_ok=True)
    made = []
    for name, text in SYNTAX_SAMPLES:
        if name == "16_very_long_line":
            payload = "x" * 620
            text = text.replace("LONGLONGPLACEHOLDER", payload)
        path = out / f"{name}.robot"
        # 统一用 UTF-8 + LF 写；CRLF / 无末尾换行由样本内容自身决定
        path.write_bytes(text.encode("utf-8"))
        made.append(path.name)
    (out / "syntax_helper.resource").write_text(SYNTAX_HELPER, encoding="utf-8")
    return made


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--xlsx", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args()

    legacy = build_legacy(args.xlsx, args.out / "legacy")
    syntax = build_syntax(args.out / "syntax")
    print(f"legacy {len(legacy)}: {', '.join(legacy)}")
    print(f"syntax {len(syntax)}: {len(syntax)} 个文件 + 1 个 resource")


if __name__ == "__main__":
    main()

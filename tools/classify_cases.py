#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
用例台账安全分级（对应 docs/adr/ADR-0013-执行安全分级与开发期禁跑令.md）

用途：读入台账 Excel，按「命令白名单」给每条用例打 L0 / L1 / L2 / LX 四档，
产出可直接给评审看的 CSV。**分级不看人写的中文描述，只看真实命令**——
描述可以写得温和，命令不会骗人。

用法：
    python tools/classify_cases.py <台账.xlsx> [--out <CSV>] [--l0-only]

退出码：0 = 正常产出；1 = 找不到表头 / 无数据行

分级口径（顺序即优先级，命中即停）：
    L2  高危·不可逆 —— 改交换机配置 / 刷固件BIOS / 装卸驱动 / 重启下电 / 删除格式化 / 断链
    L1  可逆写操作 —— 改 IP、改 mtU、启停服务、建目录、改权限（能改回来）
    L0  只读 —— 命令逐条命中只读白名单
    LX  无法判定 —— 命令列为空或含未在白名单内的动作，**默认不给 L0 资格**

设计取舍：白名单制而非黑名单制。黑名单永远漏，漏的那条可能就是刷 BIOS。
判不出来就不给资格（LX），交人工补判——宁可少放 146 条，不可错放 1 条。
"""

import argparse
import csv
import os
import re
import sys

try:
    import openpyxl
except ImportError:
    sys.stderr.write("需要 openpyxl：pip install openpyxl\n")
    sys.exit(1)

# ---------------------------------------------------------------- 分级规则
# L2：命中任一即高危。宁可误判为高危，不可漏判。
DANGER = [
    ("switch-config", r"\b(interface\s+\S+|switchport|no\s+shutdown|port-channel|"
                      r"vlan\s*\d+|spanning-tree|speed\s+\d+|negotiation\s+auto)\b"),
    ("firmware-bios", r"\b(flint|mlxfwreset|mlxburn|mlxconfig\s+-[sy]|fw_update|"
                      r"flashrom|nvupdate|\.bin\b|刷\s*(固件|BIOS)|升级\s*固件)"),
    ("driver", r"\b(rmmod|insmod|modprobe\s+-r|dkms\s+(remove|uninstall)|"
               r"apt(-get)?\s+(remove|purge)|dpkg\s+-r)\b"),
    ("reboot-power", r"\b(reboot|shutdown|poweroff|ipmitool\s+\S+\s+(power|chassis)|"
                     r"reset\b|下电|断电|重启)"),
    ("destroy", r"\b(mkfs|fdisk|parted|nvme\s+format|wipefs|dd\s+if=|shred)\b|"
                r">\s*/dev/|\brm\s+-\S|\bformat\b"),
    ("link-down", r"\bip\s+link\s+set\s+\S+\s+down\b|\bifconfig\s+\S+\s+down\b|"
                  r"\bifdown\b|\bnmcli\s+\S+\s+down\b"),
    # ethtool 自检会短暂中断链路（-t online 尤甚），按高危处理：现场可能失联
    ("ethtool-selftest", r"ethtool\s+-t\b"),
    # 真实断电动不动 PDU —— 命令列往往只写断电后的采样命令（见 TN-IMP-211）
    ("power-cycle", r"\bPDU\b|AC\s*(输入|循环|断开|供电)|切断\S{0,6}(电源|AC)|"
                    r"power\s*cycle|power\s*off|掉电"),
]

# L1：可逆写操作。能改回来的都算这一档。
#
# 注意 ethtool：它的读/写开关**靠大小写区分**（-k 读 / -K 写，-l 读 / -L 写，
# -g 读 / -G 写，-a 读 / -A 写，-S 读 / -s 写）。2026-11-04 实测踩过：
# 用 re.I 统一匹配会把 `ethtool -K ... gro off` 当成只读，226 条里漏放 30 条写操作。
# 因此 ethtool 的读写判定一律**大小写敏感**，且优先级高于白名单。
WRITE = [
    ("ethtool-write", r"ethtool\s+-[sKAGLCN]\b"),      # 大小写敏感，勿加 re.I
    ("ip-modify", r"\bip\s+(addr|route|link\s+set|neigh)\s+(add|del|set|replace)\b|"
                  r"\bifconfig\s+\S+\s+(up|down|\d)"),
    ("sysctl", r"\bsysctl\s+-w\b|echo\s+\S+\s*>\s*/proc/|\btee\s+/proc/"),
    ("service", r"\bsystemctl\s+(start|stop|restart|enable|disable|reload)\b"),
    ("file-op", r"\b(mkdir|touch|chmod|chown|ln\s+-s|cp\s|mv\s|tar\s+-?[xc])\b"),
    ("nmcli-mod", r"\bnmcli\s+(con|connection|dev|device)\s+(modify|up|down|add)\b"),
]

# L0 白名单：完整命令片段必须命中这里，才具备只读资格。
# 每项为 (正则, 是否大小写敏感)。ethtool 项必须敏感。
READ_ALLOW = [
    (r"^(sudo\s+)?(lspci|lsusb|lscpu|lsblk|lsmod|ls\s|uname|uptime|whoami|id|hostname|"
     r"dmidecode|sensors|nvme\s+list|nvme\s+id-\S+|smartctl\s+-\S+|ibv_devinfo|ibstat|"
     r"iblinkinfo|ibv_devices|mst\s+status|mstconfig\s+-d\s+\S+\s+q|"
     r"mlxconfig\s+-d\s+\S+\s+q|"
     r"ip\s+(-s\s+)?(addr|a|link|route|r|neigh|n)\s*(show|list|s|l)?$|"
     r"ip\s+-s\s+link\s+show|ifconfig$|ss\s|netstat|ping\s|traceroute|"
     r"cat\s|head\s|tail\s|grep\s|egrep\s|awk\s|cut\s|wc\s|sort\s|uniq\s|"
     r"sed\s+-n|find\s|stat\s|file\s|df\s|du\s|free\s|top\s+-b|ps\s|"
     r"dmesg|journalctl|systemctl\s+status|service\s+\S+\s+status|"
     r"i2cdetect\s+-l|i2cget\s|i2cdump\s|"
     r"ipmitool\s+\S+\s+(mc|sdr|fru|sensor|sel|lan)\b|"
     r"curl\s+(--fail|--insecure|--user|-s|-k|-\S)*\s*(https?://)|"
     r"show\s|display\s|diagnose\s|get\s)", False),
    # ethtool 只读：大小写敏感。-k/-l/-g/-a/-S/-i/-c/-m/-T 是读，-K/-L/-G/-A/-s 是写。
    (r"ethtool\s+-(i|k|l|g|a|S|c|m|T)\b", True),
    (r"ethtool\s+[a-z0-9]+\s*$", True),   # 裸 ethtool <iface> 只读
]

READ_ALLOW_RE = [(re.compile(p, 0 if cs else re.I), cs) for p, cs in READ_ALLOW]

# ethtool 的读/写靠大小写区分，这一族必须敏感匹配（-T 读 / -t 自检，-K 写 / -k 读）；
# 其余关键词不敏感。
CASESENSITIVE_DANGER = {"ethtool-selftest"}
CASESENSITIVE_WRITE = {"ethtool-write"}
DANGER_RE = [(n, re.compile(p, 0 if n in CASESENSITIVE_DANGER else re.I)) for n, p in DANGER]
WRITE_RE = [(n, re.compile(p, 0 if n in CASESENSITIVE_WRITE else re.I)) for n, p in WRITE]

SPLIT_RE = re.compile(r"[\n;]+|\s+&&\s+|\s*\|\s*(?!=)")

# ---------------------------------------------------------------- 表头识别
COL_ALIASES = {
    "code": ["测试编号", "编号", "用例编号", "case_code", "ID"],
    "title": ["测试用例", "标题", "用例名称", "title"],
    "cat1": ["一级分类", "分类", "category"],
    "cat2": ["二级分类", "子分类", "subcategory"],
    "type": ["测试类型", "类型"],
    "pre": ["预置条件", "前置条件", "precondition"],
    "step": ["测试步骤（按序执行）", "测试步骤", "步骤", "steps"],
    "cmda": ["A端命令", "A 端命令"],
    "cmdb": ["B端命令", "B 端命令"],
    "cmdsw": ["交换机配置", "交换机", "switch"],
    "expect": ["预期结果", "期望结果", "expect"],
}


def find_header(rows):
    """找表头行：同时出现「测试编号」与至少两个已知列名。"""
    for idx, row in enumerate(rows[:40]):
        cells = [str(c).strip() if c is not None else "" for c in row]
        joined = "|".join(cells)
        if "测试编号" in joined or "用例编号" in joined:
            return idx, cells
    return None, None


def map_cols(header):
    out = {}
    for key, aliases in COL_ALIASES.items():
        for i, h in enumerate(header):
            if not h:
                continue
            if any(a in h for a in aliases):
                out[key] = i
                break
    return out


def cell(row, idx):
    if idx is None or idx >= len(row):
        return ""
    v = row[idx]
    return "" if v is None else str(v).strip()


# 交换机列里的中立表述：没这些词就认为「要动交换机」
SW_NEUTRAL = ("无（", "无(", "无；", "无;", "无需", "不修改", "不改动", "不需要",
              "不改", "保持现有", "沿用", "无变化", "不用")

# 纯核对句：以这些词开头的分句不算动作描述（否则「确认端口未 shutdown」会被判高危）
STEP_SKIP = re.compile(
    r"^(?:\d+[.、)）]\s*)?(确认|检查|查看|记录|观察|读取|核对|验证|查询|统计|确保|保证|比对)"
)

# 标题里的动作词。台账命令列常常只写「读」，真正动手写在标题上：
# TN-IMP-079「UFO 功能开关 - 关闭」的命令列只有 `ethtool -k`（读），
# 实际开关是 `ethtool -K`。标题不扫就会把写操作当成只读。
TITLE_ACTION = re.compile(
    r"开关|关闭|开启|设置|修改|切换|恢复|重启|重置|断开|插拔|替换|升级|卸载|安装|"
    r"清除|写入|配置|控制|唤醒|WOL|点灯|烧录|擦除|绑定|解绑|限速|压测|老化"
)


def scan_sentences(text, regexes):
    """按句扫描，跳过纯核对句。返回第一个命中 (name, 句子片段)。"""
    for sent in re.split(r"[。；;\n]", text or ""):
        s = sent.strip()
        if len(s) < 3 or STEP_SKIP.match(s):
            continue
        for name, rx in regexes:
            if rx.search(s):
                return name, s[:48]
    return None


def classify(code, title, cmda, cmdb, cmdsw, step):
    """返回 (level, rule, reason)。

    分四处看，任一处命中高危即 L2：
      ① 交换机列 —— 非中立表述即视为改配。「逐档设置/恢复端口速率」这类不含
         interface/switchport 字样，只扫关键词抓不到，必须靠中立词反向判定。
      ② 步骤列   —— 真实动作常写在这里，命令列只是事后采样。TN-IMP-211
         「PDU 切断全部 AC 输入 200 轮」的命令列只有 cat/lspci/ethtool。
      ③ 命令列   —— 逐段命中只读白名单才授 L0。
      ④ 标题列   —— 「××开关测试」的命令列常只写读命令，动手在标题里。
    """
    # ① 交换机列
    sw = (cmdsw or "").strip()
    if sw and not any(k in sw for k in SW_NEUTRAL):
        return "L2", "L2:switch-config", "交换机列要求变更端口/速率：" + sw[:48]

    # ② 步骤列
    hit = scan_sentences(step, DANGER_RE)
    if hit:
        return "L2", "L2:step-" + hit[0], "步骤含高危动作：" + hit[1]
    step_write = scan_sentences(step, WRITE_RE)

    # ③ 命令列
    cmds = "\n".join(x for x in (cmda, cmdb) if x)
    if not cmds.strip():
        if step_write:
            return "L1", "L1:step-" + step_write[0], "步骤含写操作（命令列为空）：" + step_write[1]
        return "LX", "LX:no-command", "命令列为空，无法白名单核验"

    frags = [f.strip() for f in SPLIT_RE.split(cmds) if f and f.strip()]
    for f in frags:
        for name, rx in DANGER_RE:
            if rx.search(f):
                return "L2", "L2:" + name, f[:60]
    for f in frags:
        for name, rx in WRITE_RE:
            if rx.search(f):
                return "L1", "L1:" + name, f[:60]
    if step_write:
        return "L1", "L1:step-" + step_write[0], "命令只读但步骤含写操作：" + step_write[1]

    # ④ 标题：命令全只读，但标题说明要动手（开关/设置/重启…）
    if TITLE_ACTION.search(title or ""):
        return "L1", "L1:title-action", "命令只读但标题含动作词：" + (title or "")[:36]

    unmatched = []
    for f in frags:
        probe = re.sub(r"^\s*(sudo|ssh\s+\S+)\s+", "", f).strip().strip('"').strip("'")
        probe = probe.rstrip('"').strip("'")
        if not any(rx.search(probe) for rx, _ in READ_ALLOW_RE):
            unmatched.append(f[:50])
    if unmatched:
        return "LX", "LX:not-whitelisted", "未在白名单： " + " / ".join(unmatched[:2])
    return "L0", "L0:readonly-whitelist", "命令全命中只读白名单，步骤无动作"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("excel")
    ap.add_argument("--out", default="")
    ap.add_argument("--l0-only", action="store_true")
    ap.add_argument("--sheet", default="")
    args = ap.parse_args()

    if not os.path.exists(args.excel):
        sys.stderr.write("文件不存在: %s\n" % args.excel)
        return 1

    wb = openpyxl.load_workbook(args.excel, read_only=True, data_only=True)

    # 多 sheet 的台账里，「看板」「汇总」页也常带编号列但没有命令列。
    # 逐个试，取第一张能解析出 code + 命令列的 sheet；--sheet 可强制指定。
    picked = None
    for name in ([args.sheet] if args.sheet else wb.sheetnames):
        rows = [list(r) for r in wb[name].iter_rows(values_only=True)]
        hidx, header = find_header(rows)
        if hidx is None:
            continue
        cols = map_cols(header)
        if "code" in cols and ("cmda" in cols or "step" in cols):
            picked = (name, rows, hidx, cols)
            break
    if picked is None:
        sys.stderr.write("没有可用 sheet：需含「测试编号」及「A端命令」或「测试步骤」列\n")
        return 1
    sheet, rows, hidx, cols = picked

    out_rows = []
    for r in rows[hidx + 1:]:
        code = cell(r, cols.get("code"))
        if not code or not re.match(r"^[A-Z]{2,}-[A-Z]{2,}-\d+", code):
            continue
        level, rule, reason = classify(
            code,
            cell(r, cols.get("title")),
            cell(r, cols.get("cmda")),
            cell(r, cols.get("cmdb")),
            cell(r, cols.get("cmdsw")),
            cell(r, cols.get("step")),
        )
        sw_raw = cell(r, cols.get("cmdsw"))
        out_rows.append({
            "用例编号": code,
            "安全级": level,
            "判定规则": rule,
            "依据": reason,
            "标题": cell(r, cols.get("title"))[:60],
            "一级分类": cell(r, cols.get("cat1")),
            "二级分类": cell(r, cols.get("cat2")),
            # 不压缩成「有/无」：列里常写「无（不修改交换机）」「按需查看」，
            # 二值化会骗人。原样截 40 字，评审自己看。
            "交换机列原文": sw_raw.replace("\n", " ")[:40],
        })

    if args.l0_only:
        out_rows = [x for x in out_rows if x["安全级"] == "L0"]

    # 汇总
    from collections import Counter
    cnt = Counter(x["安全级"] for x in out_rows)
    print("数据源: %s [%s]" % (os.path.basename(args.excel), sheet))
    print("用例总数: %d" % len(out_rows))
    for lv in ("L0", "L1", "L2", "LX"):
        print("  %-3s %4d" % (lv, cnt.get(lv, 0)))
    print("\n规则命中分布:")
    for rule, n in Counter(x["判定规则"] for x in out_rows).most_common():
        print("  %-30s %4d" % (rule, n))

    if args.out:
        with open(args.out, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=list(out_rows[0].keys()) if out_rows else ["用例编号"])
            w.writeheader()
            w.writerows(out_rows)
        print("\n已写出: %s（%d 行）" % (args.out, len(out_rows)))
    return 0


if __name__ == "__main__":
    sys.exit(main())

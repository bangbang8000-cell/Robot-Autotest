#!/usr/bin/env python3
"""用例台账脱敏：把真实实验室资产替换为保留结构的假数据。

用途（ENV-04）：台账样本要入仓供 CAS-03 导入链路验证，但原表含真实服务器 /
BMC / 交换机 IP、账户名与拓扑。原样入仓等于把实验室资产清单公开，因此必须先
脱敏，且脱敏过程必须可复现、可自检。

设计要点
--------
1. **只保留用例页**：原表另含「当前测试环境」拓扑页（真实资产最密集的一页），
   导入链路用不到，直接不导出。
2. **映射一一对应**：同一真实 IP 永远映射到同一假 IP。否则「A 端 ping B 端」
   这类跨列引用会断裂，导入后对账看不出结构是否正确。
3. **自检失败即退出非零**：输出文件里若仍能匹配出真实 IP，脚本报错——不靠人看。
4. **映射表不入仓**：映射表本身就是「假名 ↔ 真名」对照，只能留在 artifacts/。

用法
----
    python tools/anonymize_ledger.py <源.xlsx> -o fixtures/case-ledger/ledger-sample-226.xlsx
    python tools/anonymize_ledger.py <源.xlsx> -o out.xlsx --map artifacts/tmp/ledger-map.csv
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from pathlib import Path

import openpyxl

# 边界必须用 ASCII 显式类，不能用 \b / \w：\w 在 Python 默认含中文，
# 「地址172.31.10.11」会因前一个字是汉字而失配。同时排除版本号尾巴：
# 「v0.2.6.0.1」里的 2.6.0.1 前面是点，不是 IP。
IP_RE = re.compile(r"(?<![0-9A-Za-z.])(?:\d{1,3}\.){3}\d{1,3}(?![0-9A-Za-z.])")
MAC_RE = re.compile(r"\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b")
# 账户名：真实环境中出现的运维账户。替换为 userNN，保留「这是个人类账户」的语义。
ACCOUNT_RE = re.compile(r"\b(ehtadmin|root|admin|evergreen|tester)\b", re.I)
# 序列号：10~14 位、含至少 6 位数字的大写串（如 E928GD264002）。型号（AS8E32-E01）含连字符，不会命中。
SERIAL_RE = re.compile(r"\b(?=[A-Z0-9]{10,14}\b)(?=[A-Z0-9]*\d{6})[A-Z0-9]+\b")

HEADER_KEY = "测试编号"
CODE_RE = re.compile(r"^[A-Z]{2,}-[A-Z]{2,}-\d+")


class Mapper:
    """按首次出现顺序分配假值，保证同一真实值全局映射到同一假值。"""

    def __init__(self) -> None:
        self.ip: dict[str, str] = {}
        self.mac: dict[str, str] = {}
        self.account: dict[str, str] = {}
        self.serial: dict[str, str] = {}

    def _ip(self, real: str) -> str:
        if real not in self.ip:
            n = len(self.ip)
            # 10.0.<段>.<主机>：避开原表所有网段（10.202 / 172.30 / 172.31 / 198.18）
            self.ip[real] = "10.0.%d.%d" % (n // 250 + 1, n % 250 + 1)
        return self.ip[real]

    def _mac(self, real: str) -> str:
        if real not in self.mac:
            self.mac[real] = "02:00:00:00:00:%02x" % (len(self.mac) + 1)
        return self.mac[real]

    def _acct(self, real: str) -> str:
        key = real.lower()
        if key not in self.account:
            self.account[key] = "user%02d" % (len(self.account) + 1)
        return self.account[key]

    def _ser(self, real: str) -> str:
        if real not in self.serial:
            self.serial[real] = "SN-%06d" % (len(self.serial) + 1)
        return self.serial[real]

    def scrub(self, text: str) -> tuple[str, dict[str, int]]:
        cnt = {"ip": 0, "mac": 0, "account": 0, "serial": 0}

        def sub_ip(m: re.Match) -> str:
            cnt["ip"] += 1
            return self._ip(m.group(0))

        def sub_mac(m: re.Match) -> str:
            cnt["mac"] += 1
            return self._mac(m.group(0))

        def sub_acct(m: re.Match) -> str:
            cnt["account"] += 1
            return self._acct(m.group(0))

        def sub_ser(m: re.Match) -> str:
            cnt["serial"] += 1
            return self._ser(m.group(0))

        text = IP_RE.sub(sub_ip, text)
        text = MAC_RE.sub(sub_mac, text)
        text = ACCOUNT_RE.sub(sub_acct, text)
        text = SERIAL_RE.sub(sub_ser, text)
        return text, cnt


def find_header(rows: list[list]) -> int:
    for i, r in enumerate(rows):
        if r and r[0] and HEADER_KEY in str(r[0]):
            return i
    raise SystemExit("未找到表头行（需含「%s」列）" % HEADER_KEY)


def main() -> int:
    ap = argparse.ArgumentParser(description="用例台账脱敏（保留结构、替换真实资产）")
    ap.add_argument("source", help="源 xlsx")
    ap.add_argument("-o", "--output", required=True, help="输出 xlsx")
    ap.add_argument("--sheet", default="详细测试流程", help="用例页名（默认「详细测试流程」）")
    ap.add_argument("--map", help="映射表输出路径（含真实值，只能放 artifacts/）")
    args = ap.parse_args()

    src = Path(args.source)
    wb = openpyxl.load_workbook(src, data_only=True)
    if args.sheet not in wb.sheetnames:
        raise SystemExit("源文件无「%s」页；现有：%s" % (args.sheet, wb.sheetnames))

    ws = wb[args.sheet]
    rows = [list(r) for r in ws.iter_rows(values_only=True)]
    hidx = find_header(rows)

    mapper = Mapper()
    total = {"ip": 0, "mac": 0, "account": 0, "serial": 0}
    out_rows: list[list] = []
    kept = 0

    for i, r in enumerate(rows):
        # 表头之前是标题/说明行，原样保留（导入器靠它们之前的空行定位表头，保留更接近真实输入）
        if i < hidx:
            out_rows.append(r)
            continue
        code = str(r[0] or "") if r else ""
        if i > hidx and code and not CODE_RE.match(code):
            continue  # 表头之后的分隔行/尾部备注
        new_row = []
        for v in r:
            if isinstance(v, str):
                v2, cnt = mapper.scrub(v)
                for k in total:
                    total[k] += cnt[k]
                new_row.append(v2)
            else:
                new_row.append(v)
        out_rows.append(new_row)
        if i > hidx:
            kept += 1

    out = openpyxl.Workbook()
    ows = out.active
    ows.title = args.sheet
    for r in out_rows:
        ows.append(r)
    outp = Path(args.output)
    outp.parent.mkdir(parents=True, exist_ok=True)
    out.save(outp)

    # ---- 自检：输出文件不得再匹配出真实 IP ----
    chk = openpyxl.load_workbook(outp, data_only=True)
    residual = []
    for row in chk[ows.title].iter_rows(values_only=True):
        for v in row:
            if isinstance(v, str):
                residual.extend(IP_RE.findall(v))
    # 输出里应当只有 10.0.x.y，出现别的网段即视为残留
    bad = [ip for ip in residual if not ip.startswith("10.0.")]

    if args.map:
        mp = Path(args.map)
        mp.parent.mkdir(parents=True, exist_ok=True)
        with mp.open("w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["kind", "real", "fake"])
            for k, v in mapper.ip.items():
                w.writerow(["ip", k, v])
            for k, v in mapper.mac.items():
                w.writerow(["mac", k, v])
            for k, v in mapper.account.items():
                w.writerow(["account", k, v])
            for k, v in mapper.serial.items():
                w.writerow(["serial", k, v])

    print("源文件      : %s" % src.name)
    print("用例页      : %s" % args.sheet)
    print("保留数据行  : %d" % kept)
    print("替换统计    : IP %d / MAC %d / 账户 %d / 序列号 %d" % (
        total["ip"], total["mac"], total["account"], total["serial"]))
    print("唯一映射    : IP %d / MAC %d / 账户 %d / 序列号 %d" % (
        len(mapper.ip), len(mapper.mac), len(mapper.account), len(mapper.serial)))
    print("输出        : %s" % outp)
    if args.map:
        print("映射表      : %s（含真实值，不得入仓）" % args.map)

    if bad:
        print("自检失败：输出中仍有非 10.0.x.y 的 IP %d 处：%s" % (len(bad), sorted(set(bad))[:5]))
        return 1
    print("自检通过：输出中 IP 全部落在 10.0.0.0/16 假地址段（%d 处）" % len(residual))
    return 0


if __name__ == "__main__":
    sys.exit(main())

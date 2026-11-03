#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MetricsCollector - infra-monitor 结构化指标登记库 (第2层)

学习要点:
1. Robot Framework 自定义 Python 库: 类名即库名, 公开方法即关键字
2. 通过 BuiltIn 读取自动变量 (${SUITE NAME} / ${TEST NAME} / ${OUTPUT_DIR})
3. 每次登记立即落盘 metrics.json - 即使测试中断, 已采集数据也保留
4. 方法名下划线在 RF 中自动转为空格: 解析_ping_输出 -> 解析 Ping 输出
"""
import json
import os
import re
from datetime import datetime

from robot.libraries.BuiltIn import BuiltIn


class MetricsCollector:
    ROBOT_AUTO_KEYWORDS = True
    ROBOT_LIBRARY_SCOPE = "GLOBAL"
    ROBOT_LIBRARY_VERSION = "1.0"

    def __init__(self):
        self._records = []

    # ---------- 内部工具 ----------
    @staticmethod
    def _num(value):
        """尽量转数值; 转不了就原样保留 (文本指标如 hostname)"""
        if value is None or isinstance(value, (int, float)):
            return value
        s = str(value).strip()
        if s == "":
            return None
        try:
            f = float(s)
            return int(f) if f.is_integer() else f
        except ValueError:
            return s

    def _flush(self):
        output_dir = BuiltIn().get_variable_value("${OUTPUT_DIR}") or "."
        path = os.path.join(output_dir, "metrics.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(self._records, f, ensure_ascii=False, indent=2)
        return path

    # ---------- 关键字 ----------
    def 登记指标(self, metric, value, unit="", target="", threshold="", note=""):
        """登记一条结构化指标并立即落盘 metrics.json。

        metric=指标名(如 ping_avg_ms) | value=数值或文本 | unit=单位
        target=目标 IP | threshold=阈值表达式(如 <90) | note=备注
        """
        record = {
            "suite": BuiltIn().get_variable_value("${SUITE NAME}") or "",
            "test": BuiltIn().get_variable_value("${TEST NAME}") or "",
            "target": str(target),
            "metric": str(metric),
            "value": self._num(value),
            "unit": str(unit),
            "threshold": str(threshold),
            "note": str(note),
            "ts": datetime.now().isoformat(timespec="seconds"),
        }
        self._records.append(record)
        self._flush()
        return record

    def 导出指标(self):
        """把已登记指标落盘并返回文件路径 (可在 Suite Teardown 显式调用)"""
        return self._flush()

    def 解析_ping_输出(self, out):
        """解析 ping stdout -> (丢包率%, 平均延迟ms)。100% 丢包时延迟为 None。"""
        text = str(out or "")
        m_loss = re.search(r"([\d.]+)% packet loss", text)
        loss = float(m_loss.group(1)) if m_loss else 100.0
        m_rtt = re.search(r"= [\d.]+/([\d.]+)/", text)
        avg = float(m_rtt.group(1)) if m_rtt else None
        return loss, avg

    def 登记_硬件指标(self, out, target):
        """解析 nvidia-smi --query-gpu=... --format=csv,noheader,nounits 输出,
        逐卡登记指标, 返回摘要文本。空输入返回空串 (无 GPU 不判失败)。"""
        text = str(out or "").strip()
        if not text:
            return ""
        summaries = []
        for line in text.splitlines():
            parts = [p.strip() for p in line.split(",")]
            if len(parts) < 6:
                continue
            idx, name, util, mem_used, mem_total, temp = parts[:6]
            prefix = f"gpu{idx}"
            self.登记指标(f"{prefix}_name", name, target=target)
            self.登记指标(f"{prefix}_util_pct", util, unit="%", target=target)
            self.登记指标(f"{prefix}_mem_used_mib", mem_used, unit="MiB", target=target)
            self.登记指标(f"{prefix}_mem_total_mib", mem_total, unit="MiB", target=target)
            self.登记指标(f"{prefix}_temp_c", temp, unit="°C", target=target)
            summaries.append(
                f"GPU{idx} {name} 利用率{util}% 显存{mem_used}/{mem_total}MiB 温度{temp}°C"
            )
        return " | ".join(summaries)

    # ---------- 汇总 (报告用) ----------
    def 汇总挂载(self, out):
        """从 df -h 输出解析: 本地挂载点数量与最高使用率, 供报告消息使用"""
        rows = []
        for line in str(out or "").splitlines():
            if line.startswith("---") or "%" not in line:
                continue
            cols = line.split()
            pct = next((c for c in cols if c.endswith("%")), None)
            if not pct:
                continue
            try:
                rows.append((float(pct[:-1]), cols[0], cols[-1]))
            except ValueError:
                continue
        real = [r for r in rows if str(r[1]).startswith("/dev/")]
        if not real:
            real = rows
        if not real:
            return "无本地挂载点信息"
        real.sort(reverse=True)
        rows = real
        top_pct, dev, mnt = rows[0]
        return "%d 个本地挂载点, 最高使用率 %.1f%% (%s on %s)" % (len(rows), top_pct, dev, mnt)

    def 汇总指标(self, path=None):
        """回读 metrics.json, 返回一行全局汇总 (总条数 + 关键指标最大值)"""
        if not path:
            outdir = BuiltIn().get_variable_value("${OUTPUT_DIR}") or "."
            path = os.path.join(outdir, "metrics.json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        numeric = {}
        for rec in data:
            val = rec.get("value")
            if isinstance(val, (int, float)) and not isinstance(val, bool):
                numeric.setdefault(rec.get("metric", ""), []).append(val)
        units = {"mem_used_pct": "%", "root_disk_pct": "%", "ping_loss_pct": "%", "ping_avg_ms": "ms"}
        parts = []
        for key in ("cpu_load_1m", "mem_used_pct", "root_disk_pct", "ping_avg_ms", "ping_loss_pct"):
            if key in numeric:
                parts.append("%s max=%s%s" % (key, max(numeric[key]), units.get(key, "")))
        return "共 %d 条 / %d 类指标 | %s" % (len(data), len(numeric), " | ".join(parts))

    def 指标名称列表(self, path=None):
        """返回本次运行 metrics.json 中出现过的指标名 (去重排序)"""
        data = self._load_metrics(path)
        return sorted({str(r.get("metric", "")) for r in data if r.get("metric")})

    def 检查指标覆盖(self, expected, path=None):
        """返回 expected 中未采集到的指标名列表 (空列表=全部命中)"""
        if isinstance(expected, str):
            expected = [expected]
        have = set(self.指标名称列表(path))
        return [m for m in expected if m not in have]

    def _load_metrics(self, path=None):
        if not path:
            outdir = BuiltIn().get_variable_value("${OUTPUT_DIR}") or "."
            path = os.path.join(outdir, "metrics.json")
        with open(path, encoding="utf-8") as f:
            return json.load(f)

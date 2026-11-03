"""iperf3 多流加载与性能前置核查。

设计要点
--------
1. **流是第一公民**：用例参数是「总流数 flows」与「承载进程数 procs」，
   每进程流数 = ceil(flows / procs)。进程数只是绕开 iperf3 单线程瓶颈的手段，
   报告与判据一律以流为单位。
2. 实测结论（2026-10-31，p1<->p2 CX-6 200G，governor=performance，-l 1M）：
   - 1 进程 × 8 流 = 22.66 Gbps（3 次中位）
   - 8 进程 × 1 流 = 187.90 Gbps（3 次中位）
   - 单进程内加流是净损失，吞吐必须靠进程横向扩展。详见 PERF-IPERF-200G-002。
3. 所有远程命令经 _exec() 执行，命令原文与完整输出写入 RF 日志（证据链约定）。
4. 密码从环境变量 LAB_PWD 读取，不落盘。
"""

from __future__ import annotations

import json
import math
import os
import time
from datetime import datetime, timezone

import paramiko
from robot.api import logger
from robot.api.deco import keyword
from robot.libraries.BuiltIn import BuiltIn


class IperfLoad:
    """iperf3 多流加载与性能前置核查关键字库。"""

    ROBOT_LIBRARY_SCOPE = "GLOBAL"
    ROBOT_LIBRARY_VERSION = "2.0"

    def __init__(self, user: str = "eht", password: str | None = None) -> None:
        self._user = os.environ.get("LAB_USER", user)
        self._pwd = password or os.environ.get("LAB_PWD", "")

    # ---------- 运行标识（三方联动锚点） ----------

    @keyword("准备运行标识")
    def prepare_run_id(self, case_id: str = "") -> str:
        """确保 RUN_ID 存在, 并写入 suite 元数据。

        RUN_ID 是 Robot Framework / RoboScope / robotdashboard 三方的唯一关联键:
        - RF:           写入 output.xml 的 <meta name="RUN_ID">
        - robotdashboard: 解析后落入 runs.metadata 与 runs.run_alias
        - RoboScope:    记录在 execution_runs.variables

        取值优先级: 命令行 --variable RUN_ID:<id> > 按 "<CASE_ID>_<时间戳>" 生成。
        生成结果用全局变量固化, 避免一次执行内多个 suite 各自生成不同 ID。
        """
        bi = BuiltIn()
        # 注意: Set Global/Suite Variable 类关键字的变量名必须用转义形式 $RUN_ID,
        # 写成 ${RUN_ID} 会先被替换成当前值, 导致 "Invalid variable name"。
        rid = str(bi.get_variable_value("${RUN_ID}", "") or "").strip()
        if not rid or rid.lower() in ("unset", "none", "null", "run_id"):
            rid = f"{case_id or 'RUN'}_{time.strftime('%Y%m%d_%H%M%S')}"
            bi.set_global_variable("$RUN_ID", rid)
        if case_id:
            bi.set_suite_variable("$CASE_ID", case_id)
            bi.run_keyword("Set Suite Metadata", "CASE_ID", case_id)
        bi.run_keyword("Set Suite Metadata", "RUN_ID", rid)
        bi.run_keyword("Set Suite Metadata", "QA_PROJECT", "gpu-nic-qa")
        # 运行期才有值，故由关键字写；CASE_ID/DUT/LINK/SW_BUILD 等静态项
        # 由 tools/assemble.py 从 YAML meta 段写进 .robot 的 Settings（AGENTS.md §4.1）
        bi.run_keyword("Set Suite Metadata", "STARTED_AT",
                       datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))
        logger.info(f"RUN_ID = {rid}")
        return rid

    # ---------- 内部 ----------

    def _connect(self, host: str) -> paramiko.SSHClient:
        if not self._pwd:
            raise RuntimeError(
                "缺少 SSH 密码：请设置环境变量 LAB_PWD，或在导入库时传入 password"
            )
        c = paramiko.SSHClient()
        c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        c.connect(host, 22, username=self._user, password=self._pwd,
                  timeout=15, allow_agent=False, look_for_keys=False)
        return c

    def _exec(self, conn: paramiko.SSHClient, tag: str, cmd: str,
              timeout: int = 180) -> tuple[str, str, int]:
        """执行命令并把「命令原文」与「原始输出」完整写入日志。"""
        logger.info(f"[{tag}] 执行命令: {cmd}")
        _i, o, e = conn.exec_command(cmd, timeout=timeout)
        out = o.read().decode("utf-8", "replace")
        err = e.read().decode("utf-8", "replace")
        rc = o.channel.recv_exit_status()
        body = out if out.strip() else "(无 stdout)"
        if err.strip():
            body += f"\n[stderr] {err.strip()}"
        logger.info(f"[{tag}] 命令原始输出:\n{body}")
        return out, err, rc

    # ---------- 链路与性能环境 ----------

    @keyword("采集端口信息")
    def link_info(self, host: str, iface: str) -> dict:
        """返回 dict: speed_mbps / driver / firmware / link_detected / mtu。"""
        c = self._connect(host)
        try:
            out, _, _ = self._exec(
                c, host,
                f"ethtool {iface} | grep -E 'Speed|Link detected'; "
                f"ethtool -i {iface} | grep -E '^(driver|firmware-version)'; "
                f"cat /sys/class/net/{iface}/mtu", 40)
        finally:
            c.close()

        info = {"iface": iface, "speed_mbps": 0, "driver": "", "firmware": "",
                "link_detected": False, "mtu": 0}
        for line in out.splitlines():
            line = line.strip()
            if line.startswith("Speed:"):
                val = line.split(":", 1)[1].strip()
                info["speed_mbps"] = int(val.replace("Mb/s", "").strip() or 0) \
                    if val.replace("Mb/s", "").strip().isdigit() else 0
            elif line.startswith("Link detected:"):
                info["link_detected"] = line.split(":", 1)[1].strip().lower() == "yes"
            elif line.startswith("driver:"):
                info["driver"] = line.split(":", 1)[1].strip()
            elif line.startswith("firmware-version:"):
                info["firmware"] = line.split(":", 1)[1].strip()
            elif line.isdigit():
                info["mtu"] = int(line)
        return info

    @keyword("核查性能前置条件")
    def perf_precheck(self, host: str, iface: str) -> dict:
        """采集影响吞吐结论的主机侧调优状态。

        这三项不改会直接污染结论：governor 为 powersave 时 CPU 会掉到 800 MHz
        （上限 3.4 GHz）；环形缓冲区停在默认 1024 会限制每次收包批量；
        mlx5 中断若全部落在单核，会与绑核的打流进程抢同一颗 CPU。

        只读，不需要 sudo。
        """
        c = self._connect(host)
        try:
            out, _, _ = self._exec(
                c, host,
                "echo __GOV__; "
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null "
                "|| echo n/a; "
                "echo __FREQ__; "
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null "
                "|| echo 0; "
                "echo __RING__; "
                f"ethtool -g {iface} 2>/dev/null | sed -n '/Current hardware settings/,$p' "
                "| grep -E '^[[:space:]]*(RX|TX):' | tr -s ' \\t' ' '; "
                "echo __NPROC__; nproc", 40)
        finally:
            c.close()

        info = {"host": host, "iface": iface, "governor": "n/a", "cur_mhz": 0,
                "ring_rx": 0, "ring_tx": 0, "nproc": 0}
        sect = None
        for raw in out.splitlines():
            line = raw.strip()
            if line.startswith("__") and line.endswith("__"):
                sect = line.strip("_")
                continue
            if not line:
                continue
            if sect == "GOV":
                info["governor"] = line
            elif sect == "FREQ":
                if line.isdigit():
                    info["cur_mhz"] = int(line) // 1000
            elif sect == "RING":
                parts = line.replace(":", " ").split()
                val = next((int(p) for p in parts[1:] if p.isdigit()), 0)
                if parts[0].upper() == "RX":
                    info["ring_rx"] = val
                elif parts[0].upper() == "TX":
                    info["ring_tx"] = val
            elif sect == "NPROC":
                if line.isdigit():
                    info["nproc"] = int(line)

        logger.info(
            f"[{host}] 性能前置: governor={info['governor']} "
            f"cur={info['cur_mhz']}MHz ring={info['ring_rx']}/{info['ring_tx']} "
            f"nproc={info['nproc']}")
        return info

    @keyword("应用性能调优")
    def apply_perf_tune(self, host: str, iface: str,
                        ring: int = 8192, irq_base: int = 16) -> dict:
        """就地应用调优（governor=performance / ring buffer / mlx5 中断亲和）。

        幂等。需要 sudo，密码经 sudo -S 传入，不落盘。
        """
        # 注意：/proc/irq/N/smp_affinity 按【十六进制】解析写入值。
        # 直接写十进制 $((1<<16))=65536 会被当成 0x65536，展开成 10 个核，
        # 反而与打流核重叠。必须用 printf '%x' 转十六进制。
        script = (
            "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do "
            "echo performance > $f 2>/dev/null || true; done; "
            f"ethtool -G {iface} rx {ring} tx {ring} 2>/dev/null || true; "
            "i=0; for irq in $(grep -E 'mlx5_comp' /proc/interrupts | cut -d: -f1); do "
            f"cpu=$(({irq_base} + i % 16)); printf '%x\\n' $((1 << cpu)) > "
            "/proc/irq/$irq/smp_affinity 2>/dev/null || true; i=$((i+1)); done; "
            "echo APPLIED"
        )
        c = self._connect(host)
        try:
            out, _err, rc = self._exec(
                c, host, f"echo '{self._pwd}' | sudo -S bash -c {script!r} 2>&1", 120)
        finally:
            c.close()
        ok = "APPLIED" in out
        logger.info(f"[{host}] 应用调优: {'成功' if ok else '失败'} rc={rc}")
        return {"host": host, "applied": ok, "output": out.strip()[:800]}

    @keyword("采集链路 RTT")
    def ping_rtt(self, host: str, target: str, count: int = 5) -> dict:
        """返回 dict: loss_percent / rtt_avg_ms。"""
        c = self._connect(host)
        try:
            out, _, _ = self._exec(c, host, f"ping -c {count} -W 2 {target}", 40)
        finally:
            c.close()

        loss, rtt = 100.0, 0.0
        for line in out.splitlines():
            if "packet loss" in line:
                try:
                    loss = float(line.split("%")[0].split()[-1])
                except Exception:
                    pass
            if "rtt min/avg/max" in line or "round-trip" in line:
                try:
                    rtt = float(line.split("=")[1].split("/")[1])
                except Exception:
                    pass
        return {"loss_percent": loss, "rtt_avg_ms": rtt}

    # ---------- 打流 ----------

    @keyword("启动 Iperf3 服务端")
    def start_servers(self, host: str, port_base: int = 5201,
                      processes: int = 8) -> int:
        """在 server 端后台起 N 个 iperf3 -s，返回实际监听端口数。"""
        c = self._connect(host)
        try:
            # 用 -x 精确匹配进程名，避免 pkill -f 误杀承载命令的 shell 自身
            self._exec(c, host, "pkill -x iperf3; sleep 1", 30)
            # setsid 使服务端脱离当前 SSH 会话, 否则 channel 关闭时被 SIGHUP 回收
            self._exec(
                c, host,
                f"for i in $(seq 0 {processes - 1}); do "
                f"setsid iperf3 -s -p $(({port_base}+i)) "
                f">/dev/null 2>&1 </dev/null & done; sleep 2", 60)
            out, _, _ = self._exec(
                c, host,
                f"ss -ltn 'sport >= :{port_base} and sport < :{port_base + processes}' "
                f"| tail -n +2 | wc -l", 30)
        finally:
            c.close()
        try:
            return int(out.strip() or 0)
        except ValueError:
            return 0

    @keyword("运行 Iperf3 多流吞吐")
    def run_multiflow(self, client_host: str, server_ip: str,
                      port_base: int = 5201, procs: int = 8,
                      streams_per_proc: int = 1, duration: int = 8,
                      block_size: str = "1M", cpu_step: int = 1) -> dict:
        """起 procs 个绑核 iperf3 客户端，每进程 -P streams_per_proc 条流。

        返回的 flows / per_flow_avg_gbps / flow_balance_cv 是判据与报告的主口径；
        procs 仅作实现信息保留，不参与判据。
        """
        procs = int(procs)
        streams_per_proc = int(streams_per_proc)
        launch = " ".join(
            f"taskset -c {i * cpu_step} iperf3 -c {server_ip} "
            f"-p $(({port_base}+{i})) -P {streams_per_proc} -t {duration} "
            f"-l {block_size} -J > /tmp/qa_ip{i}.json 2>/dev/null &"
            for i in range(procs)
        )
        run_cmd = f"rm -f /tmp/qa_ip*.json; {launch} wait; echo __QA_DONE__"
        cat_cmd = ('for f in $(ls /tmp/qa_ip*.json | sort -V); do '
                   'echo "===FILE:$f==="; cat "$f"; done')

        c = self._connect(client_host)
        try:
            _out, err, rc = self._exec(c, client_host, run_cmd, duration + 180)
            if rc != 0:
                raise RuntimeError(f"iperf3 打流失败 rc={rc}: {err[:300]}")
            out, _, _ = self._exec(c, client_host, cat_cmd, 120)
        finally:
            c.close()

        per_proc, per_flow, flows = [], [], []
        tot_recv = tot_sent = 0.0
        retr = 0
        procs_ok = 0

        for chunk in out.split("===FILE:")[1:]:
            if "===" not in chunk:
                continue
            body = chunk.split("===", 1)[1]
            try:
                end = json.loads(body)["end"]
            except Exception as exc:
                logger.warn(f"跳过无法解析的 iperf3 输出: {exc}")
                continue
            procs_ok += 1
            sent = end.get("sum_sent", {}) or {}
            recv = end.get("sum_received", {}) or {}
            rb = recv.get("bits_per_second", 0) or 0
            tot_recv += rb
            tot_sent += sent.get("bits_per_second", 0) or 0
            retr += sent.get("retransmits", 0) or 0
            per_proc.append(round(rb / 1e9, 2))

            for idx, s in enumerate(end.get("streams") or []):
                fr = (s.get("receiver") or {}).get("bits_per_second", 0) or 0
                fs = (s.get("sender") or {}).get("bits_per_second", 0) or 0
                g = (fr or fs) / 1e9
                flows.append({"proc": len(per_proc) - 1, "stream": idx,
                              "gbps": round(g, 2)})
                per_flow.append(g)

        if not per_proc:
            raise RuntimeError("未取得任何 iperf3 结果，请检查客户端 /tmp/qa_ip*.json")
        if not per_flow:
            # 极端情况：连 streams 明细都没有，退化为每进程一条流
            per_flow = list(per_proc)
            flows = [{"proc": i, "stream": 0, "gbps": g}
                     for i, g in enumerate(per_proc)]

        mean = sum(per_flow) / len(per_flow)
        var = sum((x - mean) ** 2 for x in per_flow) / len(per_flow)
        cv = round((math.sqrt(var) / mean if mean else 0), 4)

        # 重传必须按发送量归一化再判：绝对次数随吞吐线性增长，
        # 200G 链路上偶发一次队列溢出就能破百，用它做门槛会变成随机失败源。
        sent_gb = tot_sent * duration / 8 / 1e9
        retrans_per_gb = round(retr / sent_gb, 2) if sent_gb else 0.0

        result = {
            "total_gbps": round(tot_recv / 1e9, 2),
            "total_sent_gbps": round(tot_sent / 1e9, 2),
            "retransmits": retr,
            "sent_gb": round(sent_gb, 1),
            "retrans_per_gb": retrans_per_gb,
            "flows": len(per_flow),
            "procs": procs_ok,
            "procs_launched": procs,
            "streams_per_proc": streams_per_proc,
            "block_size": block_size,
            "duration": duration,
            "per_flow_avg_gbps": round(mean, 2),
            "per_flow_min_gbps": round(min(per_flow), 2),
            "per_flow_max_gbps": round(max(per_flow), 2),
            "flow_balance_cv": cv,
            "per_proc": per_proc,
            "streams": flows,
            "raw_command": run_cmd,
        }
        logger.info(
            f"[{client_host}] {procs_ok} 进程 x -P {streams_per_proc} = "
            f"{len(per_flow)} 流, 合计 {result['total_gbps']} Gbps, "
            f"每流均值 {result['per_flow_avg_gbps']} Gbps, "
            f"不均衡度 CV={cv}, 重传 {retr} 次 "
            f"({retrans_per_gb} 次/GB, 发送 {result['sent_gb']} GB)")
        return result

    @keyword("运行 Iperf3 多进程吞吐")
    def run_multiproc(self, client_host: str, server_ip: str,
                      port_base: int = 5201, processes: int = 8,
                      duration: int = 8, cpu_step: int = 2) -> dict:
        """兼容旧调用：等价于「每进程 1 条流」。新用例请用 运行 Iperf3 多流吞吐。"""
        logger.warn("运行 Iperf3 多进程吞吐 已降级为多流模型的兼容包装，"
                    "新用例请改用 运行 Iperf3 多流吞吐")
        return self.run_multiflow(client_host, server_ip, port_base,
                                  procs=processes, streams_per_proc=1,
                                  duration=duration, cpu_step=cpu_step)

    @keyword("清理 Iperf3 进程")
    def cleanup(self, host: str) -> None:
        c = self._connect(host)
        try:
            self._exec(c, host, "pkill -x iperf3; rm -f /tmp/qa_ip*.json; true", 30)
        finally:
            c.close()

    @keyword("校验判据")
    def check_criteria(self, criteria: list, measured: dict) -> list:
        """按 criteria 列表逐条校验，返回 [{'metric','ok','detail'}...]。"""
        ops = {
            ">=": lambda a, b: a >= b,
            ">": lambda a, b: a > b,
            "<=": lambda a, b: a <= b,
            "<": lambda a, b: a < b,
            "==": lambda a, b: a == b,
        }
        results = []
        for c in criteria:
            metric = c["metric"]
            op = c["operator"]
            expect = c["value"]
            actual = measured.get(metric)
            if actual is None:
                results.append({"metric": metric, "ok": False,
                                "detail": f"未采集到指标 {metric}"})
                continue
            ok = ops[op](actual, expect)
            results.append({
                "metric": metric,
                "ok": ok,
                "detail": f"{metric} = {actual} {c.get('unit','')} "
                          f"{op} {expect} {c.get('unit','')} -> {'通过' if ok else '不通过'}",
            })
        for r in results:
            logger.info(f"[判据] {r['detail']}")
        return results

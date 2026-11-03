# -*- coding: utf-8 -*-
"""RdmaPerf —— Mellanox perftest 封装（RoCEv2 / ConnectX-6 200G）

约定：
- 服务端用 setsid 常驻，避免 SSH 会话关闭被回收；
- 每条执行先 Log 原始命令，再 Log 原始输出，保证证据链完整；
- 指标按用例落盘 results/<CASE_ID>_metrics.jsonl，供报告工具消费。
"""
from __future__ import annotations

import json
import os
import re
import time
from datetime import datetime, timezone
from pathlib import Path

import paramiko
from robot.api import logger
from robot.api.deco import keyword
from robot.libraries.BuiltIn import BuiltIn

ROBOT_AUTO_KEYWORDS = True
# 整个测试运行共用一个实例：SSH 连接复用，且 Suite Setup 设置的输出目录不会被重置
ROBOT_LIBRARY_SCOPE = "GLOBAL"

# perftest 输出是固定列宽表格。注意：小消息尺寸下 perftest 会把
# "Conflicting CPU frequency values..." 警告直接粘在数据行尾部，
# 因此正则只从行首匹配，不锚定行尾。
_RE_BW = re.compile(r"^\s*(\d+)\s+(\d+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)")
_RE_LAT = re.compile(r"^\s*(\d+)\s+(\d+)\s+([\d.]+)\s+([\d.]+)")


class RdmaPerf:
    """perftest 关键字集合。"""

    def __init__(self, ssh_user: str = "eht", ssh_password: str = "",
                 metrics_dir: str = "results"):
        self._user = ssh_user
        self._pwd = ssh_password or os.environ.get("LAB_PWD", "")
        self._sess: dict[str, paramiko.SSHClient] = {}
        # 优先用环境变量指定，便于按运行目录隔离（RF 库实例作用域不可靠）
        self._dir = Path(os.environ.get("WB_METRICS_DIR") or metrics_dir)
        self._dir.mkdir(parents=True, exist_ok=True)

    # ---------------- 基础设施 ----------------
    def _connect(self, host: str) -> paramiko.SSHClient:
        c = self._sess.get(host)
        if c is not None and c.get_transport() is not None and c.get_transport().is_active():
            return c
        c = paramiko.SSHClient()
        c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        c.connect(host, 22, username=self._user, password=self._pwd,
                  timeout=20, allow_agent=False, look_for_keys=False)
        self._sess[host] = c
        return c

    def _exec(self, host: str, cmd: str, timeout: int = 120):
        c = self._connect(host)
        si, so, se = c.exec_command(cmd, timeout=timeout)
        out = so.read().decode("utf-8", "replace")
        err = se.read().decode("utf-8", "replace")
        return out, err, si.channel.recv_exit_status()

    def _logged(self, host: str, cmd: str, timeout: int = 120):
        logger.info(f"[{host}] 执行命令: {cmd}")
        out, err, rc = self._exec(host, cmd, timeout)
        logger.info(f"[{host}] 命令原始输出 (rc={rc}):\n{out}")
        if err.strip():
            logger.info(f"[{host}] 命令标准错误:\n{err}")
        return out, err, rc

    @keyword("设置输出目录")
    def set_output_dir(self, path: str = "") -> None:
        """把指标落盘位置切到本次运行的输出目录（由 Suite Setup 传入 ${OUTPUT DIR}）。"""
        if not path or not str(path).strip():
            return
        self._dir = Path(str(path))
        self._dir.mkdir(parents=True, exist_ok=True)
        logger.info(f"指标输出目录: {self._dir}")

    # ---------------- 运行标识 / 报告 metadata（AGENTS.md §4.1） ----------------

    @keyword("准备运行标识")
    def prepare_run_id(self, case_id: str = "") -> str:
        """确保 RUN_ID 存在并写 suite metadata，同时记录 STARTED_AT（UTC）。

        与 IperfLoad.准备运行标识 同名同语义，两库保持一致：
        - RUN_ID      RF / robotdashboard / RoboScope 的三方关联键，不可改名
        - STARTED_AT  UTC 起始时间，运行期才有值，故不写在 .robot 的 Settings 里
        CASE_ID / QA_PROJECT / DUT / LINK / SW_BUILD 等静态项由装配器写进 Settings，
        此处不重复设置，避免同一键出现两个来源。
        """
        bi = BuiltIn()
        rid = str(bi.get_variable_value("${RUN_ID}", "") or "").strip()
        if not rid or rid.lower() in ("unset", "none", "null", "run_id"):
            rid = f"{case_id or 'RDMA'}_{time.strftime('%Y%m%d_%H%M%S')}"
            # 变量名必须用转义形式 $RUN_ID，传 ${RUN_ID} 会先被替换成当前值
            bi.set_global_variable("$RUN_ID", rid)
        if case_id and not str(bi.get_variable_value("${CASE_ID}", "") or "").strip():
            bi.set_suite_variable("$CASE_ID", case_id)
        bi.run_keyword("Set Suite Metadata", "RUN_ID", rid)
        bi.run_keyword("Set Suite Metadata", "STARTED_AT",
                       datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))
        logger.info(f"RUN_ID = {rid}")
        return rid

    @keyword("套件初始化")
    def suite_init(self, output_dir: str = "", case_id: str = "") -> None:
        """Suite Setup 的唯一入口：先定输出目录，再写运行标识。"""
        self.set_output_dir(output_dir)
        self.prepare_run_id(case_id)

    @keyword("关闭全部会话")
    def close_all(self) -> None:
        for h, c in self._sess.items():
            try:
                c.close()
            except Exception:
                pass
        self._sess.clear()
        logger.info("已关闭全部 SSH 会话")

    # ---------------- 前置核查 ----------------
    @keyword("核查 RoCE 前置条件")
    def check_preconditions(self, host: str, device: str = "mlx5_0",
                            port: int = 1, gid_index: int = 3,
                            min_mtu: int = 4096) -> dict:
        """核查链路层、速率、端口状态、MTU、GID 就绪情况。"""
        cmd = (
            f'echo "LINK_LAYER=$(cat /sys/class/infiniband/{device}/ports/{port}/link_layer 2>/dev/null)"; '
            f'echo "RATE=$(cat /sys/class/infiniband/{device}/ports/{port}/rate 2>/dev/null)"; '
            f'echo "STATE=$(cat /sys/class/infiniband/{device}/ports/{port}/state 2>/dev/null)"; '
            f'echo "PHYS=$(cat /sys/class/infiniband/{device}/ports/{port}/phys_state 2>/dev/null)"; '
            f'echo "NETDEV=$(rdma link show 2>/dev/null | grep \'link {device}/{port}\' | grep -oE \'netdev [^ ]+\' | cut -d\' \' -f2)"; '
            'echo "GIDS_BEGIN"; show_gids 2>/dev/null; echo "GIDS_END"'
        )
        out, _, rc = self._logged(host, cmd)

        kv = {}
        for line in out.splitlines():
            if "=" in line and not line.startswith("GIDS"):
                k, _, v = line.partition("=")
                if k.isupper():
                    kv[k.strip()] = v.strip()

        netdev = kv.get("NETDEV", "")
        if netdev:
            mtu_out, _, _ = self._exec(host, f"cat /sys/class/net/{netdev}/mtu 2>/dev/null")
            try:
                mtu = int(mtu_out.strip())
            except ValueError:
                mtu = 0
        else:
            mtu = 0

        rate_txt = kv.get("RATE", "")
        m = re.match(r"(\d+)\s*Gb/sec", rate_txt)
        rate_mbps = int(m.group(1)) * 1000 if m else 0
        if "Tb/sec" in rate_txt:
            rate_mbps = rate_mbps * 1000

        gid_ready = False
        gid_line = ""
        for line in out.splitlines():
            parts = line.split()
            if (len(parts) >= 5 and parts[0] == device
                    and parts[1] == str(port) and parts[2] == str(gid_index)):
                gid_ready = "v2" in line and bool(re.search(r"\d+\.\d+\.\d+\.\d+", line))
                gid_line = line.strip()

        state = kv.get("STATE", "")
        res = {
            "link_layer": kv.get("LINK_LAYER", ""),
            "rate_mbps": rate_mbps,
            "link_speed_mbps": rate_mbps,
            "state": state,
            "phys_state": kv.get("PHYS", ""),
            "netdev": netdev,
            "mtu": mtu,
            "gid_index": gid_index,
            "gid_ready": gid_ready,
            "gid_line": gid_line,
            "state_active": "ACTIVE" in state.upper(),
            "mtu_ok": mtu >= min_mtu,
            "raw": out,
            "command": cmd,
        }
        logger.info(
            f"[{host}] RoCE 前置: {res['link_layer']} / {rate_mbps} Mb/s / "
            f"{state} / MTU {mtu} / GID[{gid_index}] v2 {'就绪' if gid_ready else '未就绪'}"
        )
        return res

    @keyword("核查两端前置条件")
    def check_both(self, server_host: str, client_host: str,
                   device: str = "mlx5_0", gid_index: int = 3,
                   min_mtu: int = 4096) -> dict:
        """两端都查一遍，合并成一份可直接断言的指标。"""
        s = self.check_preconditions(server_host, device, 1, gid_index, min_mtu)
        c = self.check_preconditions(client_host, device, 1, gid_index, min_mtu)
        res = {
            "link_speed_mbps": min(s["rate_mbps"], c["rate_mbps"]),
            "srv_rate_mbps": s["rate_mbps"],
            "cli_rate_mbps": c["rate_mbps"],
            "srv_state": s["state"],
            "cli_state": c["state"],
            "srv_gid_ready": s["gid_ready"],
            "cli_gid_ready": c["gid_ready"],
            "srv_mtu": s["mtu"],
            "cli_mtu": c["mtu"],
            "mtu": min(s["mtu"], c["mtu"]),
            "srv_netdev": s["netdev"],
            "cli_netdev": c["netdev"],
            "link_layer": s["link_layer"],
            "all_active": s["state_active"] and c["state_active"],
            "all_gid_ready": s["gid_ready"] and c["gid_ready"],
            "mtu_ok": s["mtu_ok"] and c["mtu_ok"],
        }
        logger.info(f"两端前置汇总: 速率={res['link_speed_mbps']} Mb/s, "
                    f"ACTIVE={res['all_active']}, GID就绪={res['all_gid_ready']}, "
                    f"MTU={res['mtu']}")
        return res

    # ---------------- 服务端控制 ----------------
    def _start_server(self, host: str, tool: str, args: str) -> str:
        cmd = (f"pkill -x {tool} 2>/dev/null; sleep 0.5; "
               f"setsid nohup {tool} {args} > /tmp/pf_srv.log 2>&1 </dev/null & "
               f"sleep 2; echo __SRV_UP__")
        out, _, _ = self._logged(host, cmd, 60)
        if "__SRV_UP__" not in out:
            raise RuntimeError(f"{host} 上 {tool} 服务端启动失败：{out[:300]}")
        return cmd

    def _stop_server(self, host: str, tool: str) -> None:
        self._exec(host, f"pkill -x {tool} 2>/dev/null; true", 30)

    def _server_log(self, host: str) -> str:
        out, _, _ = self._exec(host, "cat /tmp/pf_srv.log 2>/dev/null | tail -20", 30)
        return out

    # ---------------- 带宽 ----------------
    @keyword("执行 Perftest 带宽测试")
    def run_bw(self, server_host: str, client_host: str, server_ip: str,
               tool: str = "ib_write_bw", device: str = "mlx5_0",
               gid_index: int = 3, size: int = 65536, qp: int = 1,
               duration: int = 5, bidirectional: bool = False,
               extra: str = "") -> dict:
        """跑一次单向带宽测试，返回 Gb/s 等指标。"""
        args = (f"-d {device} -x {gid_index} -s {size} -q {qp} -D {duration} "
                f"--report_gbits")
        if bidirectional:
            args += " --bidirectional"
        if extra:
            args += " " + extra

        self._start_server(server_host, tool, args)
        try:
            cmd = f"{tool} {args} {server_ip}"
            out, err, rc = self._logged(client_host, cmd, duration + 180)
        finally:
            self._stop_server(server_host, tool)

        rows = [m for m in (_RE_BW.match(l) for l in out.splitlines()) if m]
        if rc != 0 or not rows:
            raise RuntimeError(
                f"{tool} 未取得结果 rc={rc}: {(err or out)[:400]}"
            )
        m = rows[-1]
        res = {
            "size": int(m.group(1)),
            "iterations": int(m.group(2)),
            "peak_gbps": float(m.group(3)),
            "bandwidth_gbps": float(m.group(4)),
            "msg_rate_mpps": float(m.group(5)),
            "tool": tool,
            "qp": qp,
            "bidirectional": bidirectional,
            "command": cmd,
            "raw": out,
        }
        logger.info(f"{tool} {size}B qp={qp}: {res['bandwidth_gbps']} Gb/s "
                    f"({res['msg_rate_mpps']} Mpps)")
        return res

    # ---------------- 延迟 ----------------
    @keyword("执行 Perftest 延迟测试")
    def run_lat(self, server_host: str, client_host: str, server_ip: str,
                tool: str = "ib_write_lat", device: str = "mlx5_0",
                gid_index: int = 3, size: int = 2, duration: int = 5) -> dict:
        """跑一次延迟测试，返回微秒级平均延迟。"""
        args = f"-d {device} -x {gid_index} -s {size} -D {duration}"
        self._start_server(server_host, tool, args)
        try:
            cmd = f"{tool} {args} {server_ip}"
            out, err, rc = self._logged(client_host, cmd, duration + 180)
        finally:
            self._stop_server(server_host, tool)

        rows = [m for m in (_RE_LAT.match(l) for l in out.splitlines()) if m]
        if rc != 0 or not rows:
            raise RuntimeError(f"{tool} 未取得结果 rc={rc}: {(err or out)[:400]}")
        m = rows[-1]
        res = {
            "size": int(m.group(1)),
            "iterations": int(m.group(2)),
            "latency_us": float(m.group(3)),
            "tps": float(m.group(4)),
            "tool": tool,
            "command": cmd,
            "raw": out,
        }
        logger.info(f"{tool} {size}B: 平均延迟 {res['latency_us']} us, {res['tps']} ops/s")
        return res

    # ---------------- 尺寸扫描 ----------------
    @keyword("执行消息尺寸扫描")
    def run_size_scan(self, server_host: str, client_host: str, server_ip: str,
                      sizes: list, tool: str = "ib_write_bw", device: str = "mlx5_0",
                      gid_index: int = 3, duration: int = 3) -> dict:
        """逐尺寸跑 -D 模式。perftest 的 -a 不支持 -D，且 -a -n 会受
        CPU 调频干扰导致小消息数值失真，故逐尺寸测量。"""
        scan = []
        for s in sizes:
            r = self.run_bw(server_host, client_host, server_ip, tool=tool,
                            device=device, gid_index=gid_index,
                            size=int(s), duration=duration)
            scan.append({"size": int(s), "bandwidth_gbps": r["bandwidth_gbps"],
                         "msg_rate_mpps": r["msg_rate_mpps"]})
        d = {r["size"]: r["bandwidth_gbps"] for r in scan}
        res = {
            "scan": scan,
            "bw_at_64k": d.get(65536, 0.0),
            "bw_at_1m": d.get(1048576, 0.0),
            "bw_max": max(d.values()) if d else 0.0,
            "bandwidth_gbps": d.get(65536, 0.0),
            "sizes_tested": len(scan),
        }
        logger.info(f"尺寸扫描: {len(scan)} 个尺寸, 64K={res['bw_at_64k']} Gb/s, "
                    f"1M={res['bw_at_1m']} Gb/s, 峰值={res['bw_max']} Gb/s")
        return res

    # ---------------- 稳定性 ----------------
    @keyword("执行稳定性测试")
    def run_stability(self, server_host: str, client_host: str, server_ip: str,
                      tool: str = "ib_write_bw", device: str = "mlx5_0",
                      gid_index: int = 3, size: int = 65536,
                      rounds: int = 3, duration: int = 30) -> dict:
        """多轮定长测量，用轮间离散度衡量稳定性（perftest 无内置周期采样）。"""
        samples = []
        for i in range(rounds):
            r = self.run_bw(server_host, client_host, server_ip, tool=tool,
                            device=device, gid_index=gid_index, size=size,
                            duration=duration)
            samples.append(r["bandwidth_gbps"])
            logger.info(f"  第 {i+1}/{rounds} 轮: {r['bandwidth_gbps']} Gb/s")

        mean = sum(samples) / len(samples)
        spread = (max(samples) - min(samples)) / mean * 100 if mean else 0.0
        res = {
            "bandwidth_gbps": round(mean, 2),
            "min_gbps": round(min(samples), 2),
            "max_gbps": round(max(samples), 2),
            "spread_pct": round(spread, 2),
            "rounds": rounds,
            "round_duration_s": duration,
            "samples": samples,
        }
        logger.info(f"稳定性: 均值 {res['bandwidth_gbps']} Gb/s, "
                    f"区间 {res['min_gbps']}~{res['max_gbps']}, 离散度 {res['spread_pct']}%")
        return res

    # ---------------- 指标落盘 ----------------
    @keyword("记录用例指标")
    def record(self, case_id: str, values: dict) -> None:
        """把一条用例的全部指标追加写入 JSONL。"""
        clean = {k: v for k, v in values.items() if k != "raw"}
        rec = {"case_id": case_id,
               "ts": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
               "values": clean}
        path = self._dir / f"{case_id}_metrics.jsonl"
        with open(path, "a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")
        logger.info(f"已记录 {case_id} 指标 -> {path}")

    @keyword("清理全部")
    def teardown_all(self) -> None:
        """套件收尾：逐主机杀掉 perftest 残留进程，再关闭 SSH 会话。"""
        for h in list(self._sess.keys()):
            try:
                self.cleanup(h)
            except Exception as exc:
                logger.warn(f"[{h}] 清理异常: {exc}")
        self.close_all()

    @keyword("清理 perftest 进程")
    def cleanup(self, host: str) -> None:
        self._exec(host, "pkill -x ib_write_bw; pkill -x ib_read_bw; "
                         "pkill -x ib_send_bw; pkill -x ib_write_lat; "
                         "pkill -x ib_read_lat; pkill -x ib_send_lat; true", 30)
        logger.info(f"[{host}] 已清理 perftest 残留进程")

#!/usr/bin/env python3
"""A6 · WebSocket 101 握手实测（stdlib only）。

为什么不用现成 ws 客户端：本机没有 `websockets` / `ws` 依赖，装一个就把「取证」
变成「改环境」。这里直接手写 RFC 6455 握手帧，只读状态行与响应头，证据可复现。

用法：
  python3 ws_probe.py                       # 默认打统一入口 :8001
  python3 ws_probe.py --port 8081           # 直连后端（绕过反代，对照用）
  python3 ws_probe.py --run-id <RUN_ID>     # 指定 runId（默认探测用一个随机 id）
"""
import argparse
import base64
import json
import os
import socket
import time

KEY = base64.b64encode(os.urandom(16)).decode()


def handshake(host, port, path, wait=2.0):
    t0 = time.time()
    sock = socket.create_connection((host, port), timeout=5)
    req = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {host}:{port}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {KEY}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "\r\n"
    )
    sock.sendall(req.encode())
    sock.settimeout(wait)
    data = b""
    try:
        while b"\r\n\r\n" not in data:
            chunk = sock.recv(4096)
            if not chunk:
                break
            data += chunk
    except socket.timeout:
        pass
    head, sep, rest = data.partition(b"\r\n\r\n")
    lines = head.decode("latin-1").split("\r\n") if head else []
    status = lines[0] if lines else "(no response)"
    headers = {}
    for line in lines[1:]:
        if ":" in line:
            k, v = line.split(":", 1)
            headers[k.strip().lower()] = v.strip()

    frame = None
    payload = rest
    try:
        if payload:
            frame = decode_first_frame(payload)
        elif sep:
            chunk = sock.recv(4096)
            if chunk:
                frame = decode_first_frame(chunk)
    except socket.timeout:
        frame = None
    sock.close()
    return {
        "target": f"{host}:{port}{path}",
        "status": status,
        "code101": "101" in status,
        "headers": headers,
        "sec_accept_ok": headers.get("sec-websocket-accept") is not None,
        "frame": frame,
        "elapsed_ms": round((time.time() - t0) * 1000, 1),
    }


def decode_first_frame(buf):
    """只解第一帧的 opcode/长度/前 120 字节文本，用于证明「连上后真有数据面」。"""
    b0 = buf[0]
    opcode = b0 & 0x0F
    masked = bool(buf[1] & 0x80)
    length = buf[1] & 0x7F
    idx = 2
    if length == 126:
        length = int.from_bytes(buf[idx:idx + 2], "big")
        idx += 2
    elif length == 127:
        length = int.from_bytes(buf[idx:idx + 8], "big")
        idx += 8
    if masked:
        idx += 4
    body = buf[idx:idx + min(length, 120)]
    text = None
    if opcode == 1:
        try:
            text = body.decode("utf-8", "replace")
        except Exception:
            text = None
    return {"opcode": opcode, "length": length, "head": text}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8001)
    ap.add_argument("--run-id", default=None)
    ap.add_argument("--wait", type=float, default=2.0)
    args = ap.parse_args()

    run_id = args.run_id or f"a6-probe-{int(time.time() * 1000)}"
    res = handshake(args.host, args.port, f"/ws/runs/{run_id}", args.wait)
    print(json.dumps(res, ensure_ascii=False, indent=2))
    return 0 if res["code101"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

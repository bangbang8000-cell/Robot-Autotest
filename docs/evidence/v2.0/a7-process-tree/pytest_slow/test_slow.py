"""A7 pytest 进程树取证用慢用例（每个 30s，确保 20s 截止点一定能命中）。

心跳：每个用例开始时向 A7_HEARTBEAT 指定的文件追加一行并 flush，
用来证明「进程是在用例执行中途被杀」，而不是启动失败或空转。
"""
import os
import time
from datetime import datetime

HEARTBEAT = os.environ.get("A7_HEARTBEAT", "")


def _beat(name):
    if not HEARTBEAT:
        return
    with open(HEARTBEAT, "a", encoding="utf-8") as f:
        f.write("%s start %s pid=%d\n" % (datetime.now().isoformat(timespec="milliseconds"),
                                         name, os.getpid()))
        f.flush()
        os.fsync(f.fileno())


def _slow(name):
    _beat(name)
    time.sleep(30)


def test_a7_slow_one():
    _slow("test_a7_slow_one")


def test_a7_slow_two():
    _slow("test_a7_slow_two")


def test_a7_slow_three():
    _slow("test_a7_slow_three")

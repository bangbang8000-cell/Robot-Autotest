"""示例用例（v2.1 / SMPL-01）：pytest 侧的四种状态。

定位与 RF 侧一致：**平台自身回归底座**，不映射业务、不碰 DUT、不联网。
本文件里 ``test_fail`` / ``test_error`` 与 xfail 是**故意**的，不得当作产品缺陷。

metadata 说明（重要）
--------------------
pytest 没有 RF 的 suite ``Metadata`` 概念，本文件用模块级 ``SAMPLE_METADATA`` 声明同一套键，
键名与 ``MetadataGate.REQUIRED`` 完全一致，便于「同一份对外报告口径」对照。

**当前限制**：``PytestKernel.fromJson`` 只读 ``tests[].outcome``，尚未读取 metadata，
因此 pytest 侧的六必填**只声明、未进报告门禁**。该缺口记在
``docs/evidence/v2.1/SMPL-01/README.md`` 的「遗留」一节，由 A2 一并处理。

跑法::

    ~/venvs/pytest-kernel/bin/python -m pytest fixtures/samples/v2.1/pytest -q
"""

import os

import pytest

SAMPLE_METADATA = {
    "CASE_ID": "SMPL-PY-STATES",
    "RUN_ID": os.environ.get("RUN_ID", "LOCAL-SMPL-0001"),
    "QA_PROJECT": "Robot-Autotest",
    "DUT": "none-synthetic",
    "LINK": "loopback-local",
    "SW_BUILD": os.environ.get("SW_BUILD", "v2.1-sample"),
    "BASELINE": "sample-v2.1",
    "OPERATOR": os.environ.get("OPERATOR", "local"),
    "STARTED_AT": os.environ.get("STARTED_AT", "1970-01-01T00:00:00Z"),
    "CASE_KIND": "sample",
}


@pytest.fixture()
def broken_resource():
    """故意在 fixture 阶段抛错，用于产出 outcome=error（区别于断言失败）。"""
    raise RuntimeError("示例用例：fixture 构造失败，用于产出 error 态")


def test_pass_equal():
    """通过态 1：最朴素的断言，任何环境都应为 PASS。"""
    assert "alpha" == "alpha"


def test_pass_container():
    """通过态 2：容器类型断言，验证解析不把长度断言误判成错误。"""
    items = [1, 2, 3]
    assert len(items) == 3
    assert 2 in items


def test_fail_assertion():
    """失败态：断言不等，失败信息形状固定（3 != 5），供聚类归一化对照。"""
    assert 3 == 5, "AssertionError: 3 != 5"


def test_skip_explicit():
    """跳过态：显式 skip，原因固定，便于断言解析结果。"""
    pytest.skip("示例用例：主动跳过，不执行任何动作")


@pytest.mark.xfail(reason="示例用例：预期失败，用于验证 xfail 不被算作 PASS")
def test_xfail_expected():
    """xfail 态：断言失败但被标记为预期，平台须单独记为 XFAIL 而非 FAIL。"""
    assert 1 == 2


def test_error_fixture(broken_resource):
    """error 态：fixture 抛异常，outcome=error，平台须与 FAIL 区分。"""
    assert broken_resource is not None

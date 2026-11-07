"""A2 六态样本：一次真实 pytest 执行覆盖 pass/fail/skip/xfail/xpass/error。

这不是伪造数据：本文件是被真实 pytest 执行的用例，故意制造六种结局，
用于验证 PytestKernel 的状态映射（CaseStatus.fromPytest）。
"""
import pytest

MISSING_STATE_MARK = "six-states-sample"


def test_pass():
    assert True


def test_fail():
    assert False, "sample: intentional failure"


@pytest.mark.skip(reason="sample: intentional skip")
def test_skip():
    assert True


@pytest.mark.xfail(reason="sample: intentional xfail")
def test_xfail():
    assert False, "sample: expected failure"


@pytest.mark.xfail(reason="sample: intentional xpass")
def test_xpass():
    assert True


@pytest.fixture
def broken_fixture():
    raise RuntimeError("sample: intentional fixture error")


def test_error(broken_fixture):
    assert True

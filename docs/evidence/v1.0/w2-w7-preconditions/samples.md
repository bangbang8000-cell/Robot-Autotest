# C2 / C3 真实样本登记（2026-11-06）

> 只登记结构与规模，**不含源码正文、IP、主机名、凭据**。G8 第 4 条的现场确认不在本文件范围内。

| 编号 | 路径 | 实测规模 | 真实性依据 | 本轮用途 |
|---|---|---|---|---|
| C2-a | `~/.hermes/hermes-agent/tests/` | 5178 个 `test_*.py`、13 个 `conftest.py` | 第三方真实工程自带测试集，非本轮生成 | W4 `--collect-only` 预检；W3 参数化 / xfail 映射 |
| C2-b | `~/deepseek-harness/` | 22 个 `test_*.py`、`pytest.ini` | 既有真实工程 | W4 包结构与 fixture 解析 |
| C3-a | `fixtures/roundtrip/legacy/*.robot` | 17 条 `TN-IMP-xxx` | 真实项目遗留用例（编号来自实际台账） | W4 RF `--dryrun` 预检；W3 命令等价 |
| C3-b | `fixtures/bench/4suite-2cases/` | 4 suite × 2 case | 既有压测 fixture | 分片合并 |

**统计命令（可复跑）**

```
find ~/.hermes/hermes-agent/tests -name 'test_*.py' | wc -l
find ~/deepseek-harness -name 'test_*.py' | wc -l
ls ~/robot-autotest/fixtures/roundtrip/legacy/*.robot | wc -l
```

# v0.4 EXE-06 失败分类 · 30 条样本证据

- 规格：`docs/release/v0.4-execution/spec.md` §4.7（优先级 `CANCELED > ENV > INFRA > ASSERT`，仅 ENV 重试 1 次）
- 样本真源：`backend/src/test/resources/fixtures/failure-class/30-samples.tsv`（表头 + 30 行，恰好 30 条）
- 被测实现：`FailureClassifier` + `FailureRuleSet.defaults()`、`RetryPolicy`（默认 maxAttempts=1）
- 判据：`classify(input)` 结果 = `expected` 列，且 `shouldRetry(实际分类, 0, 1)` = `retry` 列
- 「实际」列取值来源：测试运行时标准输出（`FailureClassifierTest` 逐条打印的 `sample id=... actual=...`），非人工填写

## 复现命令

```powershell
$env:JAVA_HOME="C:\Users\everg\.jdk\temurin-25\jdk-25.0.2"
& "C:\Users\everg\.maven\maven-3.9.16\bin\mvn.cmd" -B -f backend/pom.xml test `
  "-Djacoco.skip=true" "-Dtest=FailureClassifierTest,RetryPolicyTest,RunExecutionServiceTest"
```

实测输出（摘录）：

```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- FailureClassifierTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- RetryPolicyTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- RunExecutionServiceTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 30 条样本 → 分类结果

| # | id | 期望 | 实际 | 是否重试 | 结论 |
|---|---|---|---|---|---|
| 1 | ENV-01 | ENV | ENV | yes | 通过 |
| 2 | ENV-02 | ENV | ENV | yes | 通过 |
| 3 | ENV-03 | ENV | ENV | yes | 通过 |
| 4 | ENV-04 | ENV | ENV | yes | 通过 |
| 5 | ENV-05 | ENV | ENV | yes | 通过 |
| 6 | ENV-06 | ENV | ENV | yes | 通过 |
| 7 | ENV-07 | ENV | ENV | yes | 通过 |
| 8 | ENV-08 | ENV | ENV | yes | 通过 |
| 9 | ENV-09 | ENV | ENV | yes | 通过 |
| 10 | INFRA-01 | INFRA | INFRA | no | 通过 |
| 11 | INFRA-02 | INFRA | INFRA | no | 通过 |
| 12 | INFRA-03 | INFRA | INFRA | no | 通过 |
| 13 | INFRA-04 | INFRA | INFRA | no | 通过 |
| 14 | INFRA-05 | INFRA | INFRA | no | 通过 |
| 15 | INFRA-06 | INFRA | INFRA | no | 通过 |
| 16 | INFRA-07 | INFRA | INFRA | no | 通过 |
| 17 | INFRA-08 | INFRA | INFRA | no | 通过 |
| 18 | INFRA-09 | INFRA | INFRA | no | 通过 |
| 19 | CANCELED-01 | CANCELED | CANCELED | no | 通过 |
| 20 | CANCELED-02 | CANCELED | CANCELED | no | 通过 |
| 21 | CANCELED-03 | CANCELED | CANCELED | no | 通过 |
| 22 | CANCELED-04 | CANCELED | CANCELED | no | 通过 |
| 23 | ASSERT-01 | ASSERT | ASSERT | no | 通过 |
| 24 | ASSERT-02 | ASSERT | ASSERT | no | 通过 |
| 25 | ASSERT-03 | ASSERT | ASSERT | no | 通过 |
| 26 | ASSERT-04 | ASSERT | ASSERT | no | 通过 |
| 27 | ASSERT-05 | ASSERT | ASSERT | no | 通过 |
| 28 | ASSERT-06 | ASSERT | ASSERT | no | 通过 |
| 29 | ASSERT-07 | ASSERT | ASSERT | no | 通过 |
| 30 | ASSERT-08 | ASSERT | ASSERT | no | 通过 |

## 样本覆盖

| 类别 | 条数 | 覆盖要点 |
|---|---|---|
| ENV | 9 | `Connection refused` / `Connection reset`（含跳板机 kex）/ `timed out` / `No route to host` / DNS 解析失败 / `Permission denied (publickey,password)` / `SSH 认证失败` |
| INFRA | 9 | `no_tests_executed`、`output.xml` 缺失、`parse_failed`、`robot` 不在 PATH、`Killed`、`disk full`、`too_small`、`launch_failed`、`artifact_dir_failed` |
| CANCELED | 4 | `canceled` 标记、`cancelled` reason、`canceled` reason、`timeout` reason（其中 CANCELED-01 的文本本身是 `Connection refused`，用于证明取消优先于 ENV） |
| ASSERT | 8 | `FAIL` 断言文本、Expected/Actual、中文断言消息、列表比较、丢包率越界、用例数不符 |

## 结论

**30/30**：30 条样本的分类结果与实际重试判定全部与期望一致（ENV 重试 1 次，ASSERT / INFRA / CANCELED 一律不重试）。
另有两条显式断言同在 `FailureClassifierTest` 内：`断言失败绝不重试`、`环境失败重试一次后不再重试`，均通过。

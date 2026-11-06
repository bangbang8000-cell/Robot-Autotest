# ORCH-03 证据 · 失败聚类与重跑清单

- **执行时间**：2026-11-06 20:34（GMT+8）
- **链路**：真实 HTTP（`:8001` → `:8081`）→ 真实 MySQL `robot_dev`
- **结论**：**通过**。8 项断言全 PASS；单测 12 条 + 性能 1 条全绿；1 万条失败聚类 **407 ms**（预算 3000 ms）。

## 1. 命令原文

```bash
cd ~/robot-autotest/backend
mvn -o -B test -Dtest="FailureCluster*Test"      # 12 功能 + 1 性能
cd .. && python3 docs/evidence/v2.1/ORCH-03/run.py
```

## 2. 判据逐条核对

| 判据（DEV-PLAN §10.4） | 实测 | 结论 |
|---|---|---|
| 两次划分逐字节一致 | 同一 runId 连续两次 `GET`，`json.dumps(sort_keys=True)` 完全相同 | 通过 |
| 1 万条 ≤ 3s | **407 ms**（长尾分布：20 个高频簇 + 8000 个单条簇） | 通过 |
| 空字段入「未分类」 | 单测覆盖 `null` 与全空白两种输入，均落入固定簇 | 通过 |
| 类 + 簇两级 | 夹具 run 分出 `ASSERT`（2 条 1 簇）与 `ENV`（1 条 1 簇） | 通过 |
| 簇不存在重跑 404 | HTTP 404 `簇不存在: no-such-cluster` | 通过 |
| 簇内无用例时重跑被拒 | HTTP 400 `该簇没有关联用例，无法重跑: …` | 通过 |

## 3. 原始输出

```
# 夹具项目 projectId=29，runId=RUN-ORCH03-FIXTURE

PASS  ORCH03-01   查询返回 200              HTTP 200
PASS  ORCH03-02   总失败数=3（PASS 行不计）      total=3
PASS  ORCH03-03   分成 ASSERT 与 ENV 两类    classes=['ASSERT', 'ENV']
PASS  ORCH03-04   路径与数字不同的两条断言失败归为 1 簇  簇数=1 键=<path>:<n>
PASS  ORCH03-05   环境失败单独成簇（不混进断言簇）      簇数=1 键=Connection refused to <ip>
PASS  ORCH03-06   同一 runId 两次查询逐字节一致    diff 为空
PASS  ORCH03-07   不存在的簇重跑返回 404         HTTP 404 message=簇不存在: no-such-cluster
PASS  ORCH03-08   簇内无用例时重跑返回 400        HTTP 400

合计 8 项，FAIL 0 项
```

单测与性能：

```
Tests run: 12, Failures: 0 -- FailureClusterServiceTest
[perf] 1 万条失败聚类耗时 407 ms
Tests run: 1, Failures: 0 -- FailureClusterPerfTest
```

## 4. 被实测推翻的两处假设（必须留痕）

### 4.1 PRD 写的失败字段位置是错的

PRD §5.1 与 FR-v2.1-04 原文按「`failure_reason` 优先，缺失时取 `failure_message` 首行」定簇键，隐含假设这两个字段在**用例级**。实测：

- `test_result` 的列是 `verdict` / `duration_ms` / **`failure_msg`**（V1 基线），没有 `failure_reason` / `failure_message`；
- `failure_reason` / `failure_class` / `failure_message` 都在 **`run` 表**（V4_1 / V4_3），是**整轮**结论。

因此实现改为：**簇键只取 `test_result.failure_msg`**；`run` 表字段只作为「整轮被判成什么」的上下文展示（`runStatus` / `runFailureClass`）。PRD 已按此订正。

### 4.2 真实链路抓到租户守卫的登记错误

首轮实跑直接 HTTP 500：

```
表 test_result 未配置租户策略：请在 TenantGuard 白名单或黑名单中登记
```

原因：`test_result` **没有 `project_id` 列**，租户条件由 `JOIN run ON r.id = t.run_id` 提供。守卫登记表名必须是被 join 的 `run`，否则会要求一条 `test_result` 永远写不出的条件。

**这个错误单测发现不了**：单测用内存仓储，根本不经过 `TenantGuard`。修完后复跑 8 项全 PASS。

## 5. 设计决定

| 决定 | 理由 | 反例 |
|---|---|---|
| 簇**不落库** | 归一化规则一改，历史簇就该重算；落库会让新旧规则的簇并存 | 落库 → 改规则后同一条失败有两套簇，谁也说不清哪个对 |
| 排序固定为「类别枚举序 → 条数倒序 → 簇键字典序」 | 确定性是硬判据；按时间或 HashMap 迭代序会让两次刷新顺序不同 | 按 count 排序不带 tie-break → 同条数的簇每次位置漂移 |
| 归一化先替路径再替数字 | 反过来会把路径里的 `v2.1` 先打成 `<n>`，让不同路径看起来相同 | 先替数字 → `/v2.1/a` 与 `/v2.2/b` 都变成 `<n>/...` 误合并 |
| 样例最多 3 条，`caseIds` 不截断 | 展示要短，重跑要用全量 | 截断 caseIds → 重跑漏掉同簇的第 4 条以后 |
| 重跑不另判安全级 | `CaseRunService` 已有 L2/LX 拦截（ADR-0013），两处判定必然分叉 | 本类再判一次 → 两处规则不一致时以谁为准说不清 |

## 6. 遗留

| # | 事项 | 影响 | 归属 |
|---|---|---|---|
| 1 | 簇键 40 字符截断可能把长尾不同的簇截成同键 | 极端情况下两个不同的长错误信息合并 | 已写入 `FailureClusterKey.MAX_KEY_LENGTH` 注释；需要时改成「截断 + 短哈希」 |
| 2 | 前端只按 runId 查询，未与运行详情页打通入口 | 用户要手动贴 RUN_ID | 路由 `/runs/failure-clusters` 已就位，入口按钮留给 ORCH-02 一并做 |
| 3 | `rerun` 未回填「重跑与被重跑 run」的关联 | 无法从报告反查「这批失败重跑过几次」 | `run.retry_of_run_id` 字段已存在（V4_3），接线留待后续 |

## 7. 文件

| 文件 | 内容 |
|---|---|
| `run.py` | 验收脚本（自建夹具 run，每次运行前清理，可反复跑） |
| `run.log` | 本次运行的完整输出 |

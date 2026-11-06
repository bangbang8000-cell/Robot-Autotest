# 长历史压测收口（RPT-03 / RPT-04 / PLN-02 三条「未压测」残余）

- 时间：2026-11-06
- 环境：WSL Ubuntu-24.04 + 本机 MySQL（`robot_dev`）+ 本机后端 `127.0.0.1:8081`（已按当前 HEAD 构建运行）
- 脚本：[longhist-e2e.sh](longhist-e2e.sh) ｜ 运行日志：[logs/01-final.log](logs/01-final.log)
- 结果：**66 项断言 0 失败**，数据全部清理（运行 / 结果 / 审计 / 项目 / 令牌计数均归零）

## 1. 这次收的是哪三条残余

| 包 | 原残余原文 |
|---|---|
| RPT-03 | 长历史未压测 |
| RPT-04 | 十万级 `test_result` 下趋势取点与通过率聚合耗时未知 |
| PLN-02 | 分页与时间范围未在真实长历史数据下压测（大 page 的 OFFSET 退化未验） |

三条都属于「功能已验、体量没验」。因此本轮**不改一行业务代码**，只造真实体量、打真 HTTP、读真数据。

## 2. 数据构造（一次性项目，末尾删净）

| 档位 | 运行数 | `test_result` | 审计事件 | 时间跨度 |
|---|---|---|---|---|
| A | 2,000 | 100,000 | 100,000 | 2024-01-01 起每 30 分钟一次，41.7 天 |
| B | 6,000 | 300,000 | 100,000（同 A） | 125 天 |

构造口径：每次运行 50 条用例（`Suite.Case01`~`50`），每 10 个全局序号 1 条 `FAIL`，即每次运行恒为 45 PASS / 5 FAIL；审计事件每行 `detail` 带唯一 `downstream=N` 序号（1~100000），用来验证深翻页没有串行错位。

取样方式：每个读接口连打 **5 次**，取 min / 中位 / max；另测一次「项目列表 page=1」作为固定成本参照（22ms 中位，含鉴权与 Jackson 序列化）。

## 3. 档位 A（10 万行）实测

| 接口 | 中位 | max | 预算 | 读数校验 |
|---|---|---|---|---|
| 项目列表（固定成本参照） | 22ms | 26ms | 300ms | — |
| `GET /reports/{runId}/trend?limit=50` | 48ms | 64ms | 1000ms | 2000 点里取最近 50（R1951~R2000，升序返回）、`baselineMs=551`、`deviated=false` |
| `GET /reports/trend/pass-rate?granularity=day&limit=52` | 31ms | 38ms | 1000ms | 42 个日桶，PASS 合计 90,000 / FAIL 合计 10,000（与造数完全一致） |
| `GET /reports/{runId}/compare` | 34ms | 41ms | 1000ms | 自动基线取前一轮 R1999；50 行全量用例；`newFail=0 newPass=0 bothFail=5 notRun=0` |
| `GET /reports/{runId}/baseline-candidates` | 23ms | 24ms | 500ms | 1999 条历史里截到 20 条，最新一条为 R1999 |
| `GET /audit?page=1&size=20` | 155ms | 173ms | 1000ms | total=100000，首条为第 100000 号（倒序） |
| `GET /audit?page=5000`（OFFSET 99980） | 433ms | 545ms | 1500ms | 20 条，末条恰好是第 1 号 → 深翻页未错位 |
| `GET /audit?page=5001`（越界） | — | — | — | 0 条但 `total` 仍为 100000 |
| `GET /audit?from=2025-06-01&to=2025-06-30` | 47ms | 55ms | 1000ms | total=8640，与库侧同日窗口 count 一致 |

## 4. 档位 B（30 万行）增长曲线

| 接口 | 10 万行 | 30 万行 | 结论 |
|---|---|---|---|
| trend（limit=50） | 48ms | 39ms | 行数×3，耗时无可见增长（仍在固定成本区间） |
| pass-rate（day, limit=52） | 31ms（2000 run） | 32ms（6000 run） | 同上；52 桶截断正确，每桶 `total=pass+fail+skip`、`passRate` 自洽 |

## 5. 结论

1. **三条残余关闭**：10 万级 `test_result`、2000 次运行历史、10 万条跳过事件、OFFSET 99980 深翻页，全部有真实读数与断言，无一项踩穿预算。
2. **RPT-04 残余的表述需要修正**：`pass-rate` 的 SQL 是 `GROUP BY DATE(run.started_at)` 对 `run` 表聚合，**不读 `test_result`**，所以它的成本只跟运行次数相关，跟 `test_result` 行数无关。6000 次运行（125 天）仍是 32ms。原残余把「十万级 test_result」和「通过率聚合」绑在一起，前提不成立，本轮已按真实口径改写（见 WBS 第 80 行）。
3. **trend / compare 在 `test_result` 上没有 `case_key` 索引**，是全表按 `case_key` 等值过滤 + 关联 `run` 取 env；30 万行实测仍在 50ms 内，不构成瓶颈。v1.0 收尾阶段**刻意不动 schema**（本版唯一动 schema 的包是 AST-05），若后续数据量再上量级，优先补 `(case_key)` 或 `(run_id, case_key)` 覆盖索引。
4. **深翻页确认线性退化**：首页 155ms → OFFSET 99980 433ms，**2.78×**，在 5× 预算内；绝对耗时主要来自每页都要 `COUNT(*)` 全量过滤，而不是 OFFSET 本身。若跳过事件将来破百万行，需要 keyset 分页（`occurred_at < ?` 续页）或 `(project_id, occurred_at, id)` 覆盖索引。

## 6. 复跑

```bash
cd ~/robot-autotest/docs/evidence/v1.0/longhist
bash longhist-e2e.sh 2>&1 | tee logs/00-run.log
```

前置：`.config/robot-autotest/env` 里的 `DB_*` 可用、后端在 `127.0.0.1:8081` 运行。脚本自带清理，中断后重跑也会先删同名历史项目（`EVD-LONGHIST-%`）。

## 7. 未覆盖

- **MySQL 冷缓存**：后端常驻、缓冲池已热，本表是热态数字；冷启动首查未测。
- **并发**：全部请求串行单客户端，未压并发打满。
- **浏览器侧渲染**：趋势图/对比表的 100k 行场景渲染耗时不在本轮范围（RPT-03/RPT-04 的「浏览器回归」残余仍待人工）。

# EXE-02 真库并发唯一键验收

## 这一份补的是什么缺口

EXE-02 原来置 `待验`，理由是「**本机并发是内存语义**（`putIfAbsent` 复刻唯一键）」。
`LeaseConcurrencyTest` 用 100 个线程争同一 key、断言恰 1 个成功 —— 但那是在 JVM 内存里
模拟唯一键，真实数据库的隔离级别、`INSERT` 冲突行为、连接池排队都没进入判定。

本脚本把「内存语义」换成**真的并发热点**：

- `resource_lease` 的唯一键 `uk_resource_lease_key(resource_key)` 是租约互斥的**唯一机制**
  （`ResourceLeaseRepositoryJdbc.tryAcquire` 就是一条纯 `INSERT`，靠唯一约束挡并发）；
- 用 **100 个**独立数据库连接在同一屏障后同时插同一个 `resource_key`（对齐 `TC-v0.4-101` 的
  「100 线程」口径）；
- 成功数必须**恰好为 1**，其余必须都是 `1062`（`ER_DUP_ENTRY`），不得出现任何其他错误。

## 复跑

```bash
cd ~/robot-autotest
python3 docs/evidence/v0.4/lease-concurrency/run.py --workers 100   # TC-101 口径
python3 docs/evidence/v0.4/lease-concurrency/run.py                  # 默认 64，同样 PASS
```

前置：`robot_dev` 库存在且已迁到最新（`resource_lease` / `resource_lease_history` 两表在位）；
`~/.config/robot-autotest/env` 里有 `DB_USERNAME` / `DB_PASSWORD`。

## 结果

**PASS**，逐条见 `run.log`（脚本每次重跑会重写，`resource_key` 带随机后缀）。

`--workers 100`（对齐 `TC-v0.4-101`「100 线程抢同一 `resource_key`」）：

| 指标 | 实测 | 期望（TC-v0.4-101） |
|---|---|---|
| 并发请求数 | 100 | 100 |
| 成功插入 | **1** | **恰好 1** |
| 唯一键冲突（1062） | 99 | 99 |
| 其他错误 | 0 | 0 |
| 该 key 落库行数 | 1 | 1 |

`--workers 64` 与 `--workers 100` 都跑过，两次都是「恰 1 成功 / 其余全 1062 / 0 其他错误」，
说明结论与并发数量级无关（并发度只改变冲突条数，不改变「只有一个赢家」）。

`lease-conflict-0.sql` 三条判定同轮跑：

| 查询 | 结果 | 判定 |
|---|---|---|
| Q1 `held` 租约行数 | 1 | 参考值 |
| Q2 同一 `resource_key` 多行 | 空集 | PASS |
| Q3 历史持有区间重叠对 | 0 | PASS 0 |

## 一处被修正的事实：库是 MySQL，不是 MariaDB

第一版脚本和 WBS 里都写着「真实 MariaDB」，**这是错的**。实测：

```
$ select version(), @@version_comment;
8.0.46-0ubuntu0.24.04.4	(Ubuntu)
```

ADR-0002 里 MariaDB 一直是「待验」项，把 MySQL 的并发结论安到 MariaDB 头上属于
张冠李戴。修正做法：

1. 脚本里不再写死引擎名，改为运行时 `SELECT VERSION()` 取真实值并写进日志抬头；
2. WBS 的 EXE-02 行同步改成「MySQL 8.0.46」，MariaDB 的并发结论仍为空（未验）。

`1062` 这个错误码 MySQL 与 MariaDB 相同，结论本身不受影响，但**证据里的署名必须是真的**。

## 边界说明（不夸大）

- 只压了 `tryAcquire` 的**单点互斥**（同一 key 同时抢），没有压「不同 key 并发 + 大量连接池
  争用」下的整体吞吐；`TC-v0.4-102`（100 线程抢 10 个 key、总成功 10）仍只有单测覆盖。
- 100 连接是单进程多线程各自开连接，不是多机；跨进程/跨节点的互斥依赖的仍是同一套唯一键，
  但网络分区、时钟漂移不在覆盖范围内。
- Q3 判的是历史区间重叠，当前库该表行数很少（夹具环境），**不能**当作历史数据规模下的性能结论。

# 压测固定 fixture（EXE-07 / T7-1）

本目录是 v0.4 执行域 4 并发压测的**固定用例形态**，只服务于并发编排压测，
不代表任何业务用例，也不得作为功能验收依据。

## 1. 固定形态

| 项 | 值 |
|---|---|
| 套件数 | 4（`4suite-2cases/suite1.robot` ~ `suite4.robot`；RF 按文件名派生套件名 `Suite 1` ~ `Suite 4`，`Documentation` 标注为 `Bench Suite 1` ~ `Bench Suite 4`，执行时另用 `--name` 给整轮命名） |
| 每套件用例数 | 2（`Bench-N-A` / `Bench-N-B`，N 为套件序号） |
| 单条用例耗时 | 固定 `Sleep 6s`（外加一条 `Log` 说明压测形态） |
| 用例总数 | 8 |
| 理论串行总时长 | 8 × 6s = **48s** |
| 标签 | 每条用例带 `[Tags] bench`，可用 `--include bench` 过滤 |

固定 Sleep 的目的是让「并发编排」成为唯一变量：用例本身不访问任何设备、不读网络、
不做断言，耗时恒定，因此耗时的差异只可能来自调度形态。

## 2. 加速比口径

脚本 `scripts/bench-4way.sh` 一轮内跑三种形态：

| 形态 | 含义 |
|---|---|
| `serial` | 单进程串行跑 4 个套件（`robot` 一次传入 4 个路径）——基线 |
| `p4` | `pabot --processes 4`——目标形态 |
| `p2` | `pabot --processes 2`——对照组 |

判定口径（与 `docs/evidence/v0.4/bench-20rounds.txt` 一致）：

- **speedup** = `serial 平均耗时 / p4 平均耗时`，**平均 ≥ 2.5x**。
- **最差一轮** speedup 同样按上式逐轮计算，**≥ 2.2x**。
- **租约冲突** = p4 产物日志（含 `pabot_results/pabot_manager.log`）中
  `Failed to acquire` / `lease conflict` 一类关键字的命中行数，**必须为 0**。
- 另附 `overhead` = p4 相对理论 1/4 的额外耗时占比，`p95` = p4 逐轮耗时的 P95（仅作参考，不进门槛）。

理论最优：48s 串行 → 12s（4 并发）；实测门槛 2.5x 对应约 19.2s，
即允许约 7.2s 的 pabot 启动 / 编排 / 结果合并开销。

## 3. 独占环境要求

压测对机器负载敏感，**不独占的机器上跑出来的数字不能进证据**：

1. 149 上不得有其他并发任务：不跑平台执行、不跑 `mvn -Pit`、不跑前端构建、
   不同时开 `robotdashboard` 的入库脚本。
2. 无残留进程：跑前确认 `ps -ef | grep -E 'pabot|robot' | grep -v grep` 为空；
   上一轮被中断时残留的 `pabot` 会抢占 CPU，直接让耗时失真。
3. 产物目录干净：脚本每轮会清空 `<artifacts>/round-NN`，但仍建议先确认
   `artifacts/bench/` 下没有手工文件。
4. 环境变量与 spec §4.4 一致：`TZ=Asia/Shanghai`、`LANG=C.UTF-8`、`PYTHONUNBUFFERED=1`，
   脚本内部已固定，无需手工 export。

## 4. 复跑命令

### 149（唯一可产出正式证据的环境）

```bash
cd ~/robot-autotest            # 仓库根（按 149 实际检出路径为准）
ps -ef | grep -E 'pabot|robot' | grep -v grep   # 应为空
bash scripts/bench-4way.sh --rounds 20 --venv "$HOME/rf-stack/.venv/bin"
```

对照组的 2 并发结果用同一条命令的 `p2` 部分即可（同一轮内同时产出），
如需单独复跑 2 并发对照：

```bash
bash scripts/bench-4way.sh --rounds 20 --venv "$HOME/rf-stack/.venv/bin" \
    --artifacts "$HOME/bench-2p"
```

### 本机（Windows）

本机**没有 RF / pabot**，只能验证脚本语法与参数行为，不能产出压测数字：

```powershell
bash -n scripts/bench-4way.sh          # 必须 exit 0
bash scripts/bench-4way.sh --help      # 只打印用法
```

直接不带参数运行会因缺 `~/rf-stack/.venv/bin/pabot` 以 **exit 3** 退出并打印
「环境不足」，这是预期行为——脚本不会在环境不足时输出任何占位数字。

## 5. 退出码

| 码 | 含义 |
|---|---|
| 0 | 全部轮次跑完并已打印统计 |
| 1 | 清理路径被安全兜底拒绝（正常不会出现） |
| 2 | 参数非法 |
| 3 | 环境不足（无 venv / 无 pabot / 缺 fixture / 产物目录不可写 / date 无纳秒） |
| 4 | 执行失败（某模式非零退出或未产出 `output.xml`） |

## 6. 与 spec 的对应

- spec §1.1「4 并发压测」：固定 fixture + 可复跑脚本，20 轮报告（本机只交付脚本，实跑留 149）。
- spec §4.4 pabot 编排环境变量：`TZ` / `LANG` / `PYTHONUNBUFFERED` / PATH 前置 venv。
- spec §6 证据清单：`bench-20rounds.txt`、`bench-2process.log`，首行 `[待149实跑]`。

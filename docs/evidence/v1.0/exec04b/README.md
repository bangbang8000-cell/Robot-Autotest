# EXEC-04 残余收口：磁盘回落时的目录套件查重（2026-11-06）

## 1. 缺口

EXEC-04 的请求期预检有两条取数分支：

- 项目在 `robot_file` 有脚本 → 数据库优先（ADR-0005 真源）；
- 数据库为空 → 回落到磁盘 `execution.workspace-root`。

两条分支的扫描范围不一致：

| 分支 | 传目录时的行为 |
|---|---|
| 数据库 | 递归扫描目录下所有 `.robot`，同名测试登记为冲突 |
| 磁盘回落 | `Files.isRegularFile` 判否即 `continue`，**目录被整个跳过** |

后果：尚未迁入文件工作区的项目，若一次选中整个目录，`--test` 的全局过滤会让目录下同名的
未勾选测试一起跑，而预检不报错——正是 EXEC-03 残余里「同名测试全局匹配」在磁盘分支上的漏口。

## 2. 做法

`ExecutionRequestPrecheck.requireUnambiguousTestNamesOnDisk`：

- 新增 `robotFilesUnder(Path)`：文件直接返回；目录 `Files.walk` 递归收集 `.robot` 并按路径排序；
- 冲突登记改用相对工作区根的路径（`suites/sub/b.robot`），与数据库分支的 `robot_file.path` 同口径；
- 不可读目录按空处理（与既有 `read()` 的容错一致，不因一个坏目录把预检变成 500）。

数据库分支未改动。

## 3. 用例（`ExecutionRequestPrecheckTest`，8 → 10 条）

| 用例 | 断言 |
|---|---|
| 磁盘回落：目录套件递归扫描子目录，重名同样拒绝 | 400 + INVALID_ARGUMENT，消息含「重复」与 `suites/sub/b.robot` |
| 磁盘回落：目录下测试名唯一放行 | 不抛异常 |

原有 8 条（磁盘/数据库的路径存在性、测试名重名、空清单放行）全部保持通过。

## 4. 门禁读数

| 项 | 结果 |
|---|---|
| `tools/subset_font.py` | PASS |
| 前端 `vitest run` | 28 文件 / 202 用例通过 |
| 前端 `vue-tsc --noEmit` | 0 错误 |
| 前端 `vite build` | `built in 8.56s` |
| 后端 `mvn -o -B test` | **600** 通过（本批前 598），0 失败 / 0 错误 |
| `scripts/lint.sh` | PASS |
| `check_release_docs.py` | PASS |
| `check_wbs_consistency.py` | PASS（96 包 / 待验 83 / 已完成 13） |

日志见同目录 `01-subset-font.log` … `08-wbs.log`。

## 5. 仍未验

1. 真实 pabot 链路下「目录套件含重名测试 → 请求期 400」的端到端读数未跑（单测已覆盖判定逻辑）。
2. `--test` 实跑效果仍待验收人现场确认（ADR-0013），与本批无关。
3. 工作区需先同步脚本这一前置不变（`workspace-root` 默认 `~/robot-test/infra-monitor`）。

# EDT-09 保存链路真实验收（真实 RF `--dryrun`）

## 这一份补的是什么缺口

EDT-09 原来置的是 `待现场`，理由是「本机无 RF，`--dryrun` 真实拦截验不了」。
本机 WSL 有 `~/rf-stack/.venv`（Robot Framework 7.5.1），所以这条可以真判。

链路：`PUT /api/editor/save` → `EditorSaveService` → `ProcessRobotSyntaxValidator`
（起子进程跑 `robot --dryrun`）→ 通过则写 `robot_file` + `robot_file_version`。

## 复跑

```bash
cd ~/robot-autotest
python3 docs/evidence/v0.3/edt09-save-chain/run.py
```

前置：8001/8081 在跑；工程师账号可登录（夹具同名 `qa-e2e-engineer`）；
`robot-autotest-backend.service` 必须带 drop-in
`~/.config/systemd/user/robot-autotest-backend.service.d/20-editor-robot.conf`：

```ini
[Service]
Environment=APP_EDITOR_ROBOT_COMMAND=/home/evergreen/rf-stack/.venv/bin/robot
```

**没有这个 drop-in 会全线失败**，报「语法校验失败，`/usr/bin/python3: No module named robot`」——
systemd 的 PATH 里没有 venv。这是实测踩到的第一处坑。

## 结果

**10 / 10 通过**，逐条见 `run.log`。

| # | 断言 | 实测 |
|---|---|---|
| EDT09-01 | 合法 `.robot` 保存成功 | HTTP 200 |
| EDT09-02 | 保存时**真的调用了** RF | `syntaxChecked=true` |
| EDT09-03 | 语法错误内容被 RF `--dryrun` 拦截 | HTTP 400 |
| EDT09-04 | 拦截消息带行号线索 | `…语法校验失败，第 5 行：[ ERROR ] Error in file '/tmp/editor-syntax-…'` |
| EDT09-05 | 内容未变再存 `changed=false` | HTTP 200 `changed=False` |
| EDT09-06 | 内容未变时版本不增 | `version=1`（期望 1） |
| EDT09-07 | 过期 `baseVersion` 保存被拒（乐观锁） | HTTP 409 |
| EDT09-08 | 版本历史可列 | HTTP 200 `versions=1` |
| EDT09-09 | 回滚生成新版本（版本号递增） | HTTP 200 `version=2 原=1` |
| EDT09-10 | 回滚不删历史 | 回滚前 1 → 回滚后 2 |

## 本轮修掉的严重缺陷：语法校验形同虚设

第一版跑到 `EDT09-03` 直接红：**语法错误的内容保存成功，HTTP 200**。

实测 RF 的行为（`probe_rf_dryrun.sh`）：

```
$ robot --dryrun bad.robot
[ ERROR ] Error in file 'bad.robot' on line 5: ...
$ echo $?
0
```

**Robot Framework 7.5.1 对语法错误只打印 `[ ERROR ]`，退出码仍是 0**；
只有「用例执行失败」才返回非 0。而 `ProcessRobotSyntaxValidator` 原来只判退出码：

```java
if (process.exitValue() != 0) return parseFailure(output);
return SyntaxReport.pass();          // ← 语法错误走到这里，直接放行
```

修法：抽出纯函数 `looksFatal(exitCode, output)`，退出码为 0 时**还要看输出里有没有
`[ ERROR ]` 行**；并补 3 条回归单测 `EDT09-REG-01/02/03` 把语义锁死：

| 用例 | 断言 |
|---|---|
| EDT09-REG-01 | `exit=0` + 输出含 `[ ERROR ]` → 必须判失败 |
| EDT09-REG-02 | `exit!=0` + 输出为空 → 必须判失败 |
| EDT09-REG-03 | `exit=0` + 正常输出（无 `[ ERROR ]`）→ 必须判通过 |

修完 `EDT09-03` 转绿，10/10。

## 边界说明（不夸大）

- `--dryrun` 只做**语法/结构**校验，不加载库，所以「引用了不存在的库」这类问题拦不住。
  真正的执行失败要等 EXE 侧跑起来才看得见。
- 本验收覆盖单文件保存与回滚，**不覆盖**多人同时编辑同一文件的冲突合并（只验了乐观锁
  单边 409）。
- 清理语句踩过一次坑：`robot_file_version` 只有 `robot_file_id`，没有 `project_id`。
  正确顺序是「查 `robot_file.id` → 按 `robot_file_id` 删版本 → 删文件」。

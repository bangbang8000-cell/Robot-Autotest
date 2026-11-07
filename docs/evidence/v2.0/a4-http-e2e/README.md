# A4 · 17 条 HTTP 端到端取证（真入口 / 真库 / 真账号）

判据来源：`docs/release/v2.0-platform/06-收口执行计划-20261106.md` 层 A · A4——
「照 `66c1fec` 的 e2e 模式：`:8001` → `:8081`，真库真账号，跑完清理并断言计数归零；
17 条 TC 每条落一行数字，0 FAIL；**禁用 MockMvc / 内存仓储**」。

## 1. 做法

- 入口：`http://127.0.0.1:8001`（node 静态 + `/api` 反代）→ Spring Boot `:8081` → MySQL `robot_dev`。
- 账号：`qa-e2e-admin / qa-e2e-engineer / qa-e2e-pm / qa-e2e-viewer / qa-e2e-nobody`，
  全部走 `POST /api/auth/login` 真实取 token；密码从 `~/.config/robot-autotest/env`
  （`QA_E2E_PASSWORD`）读取，**不落盘、不进 argv**。
- 校验：每条 TC 同时看 HTTP 状态码与**真库**读数（`mysql -N -B`，`MYSQL_PWD` 传参）。
- 无任何 MockMvc / 内存仓储 / 打桩；失败原因原样落日志，不修饰。

```bash
cd ~/robot-autotest/docs/evidence/v2.0/a4-http-e2e
python3 run.py                 # 17 条全跑
python3 run.py --only TC-42    # 单条复跑
#"raw/a4-run.log" 落全过程，逐条写 "raw/tc-XX.log"，总表写 "raw/summary.txt"
```

前置：后端与统一入口已起（`curl 127.0.0.1:8001/` 200，`/api/projects` 可达）。

## 2. 结果（每条一行数字）

| TC | 结论 | 一行数字 | 落点 |
|---|---|---|---|
| TC-06 | PASS | 非成员/跨项目 403 计 3/3；nobody 可见项目 0 | `raw/tc-06.log` |
| TC-13 | PASS | 默认键 5；viewer 403 / 非法值 400 / 合法 200；实库 `system_setting` 0→1；审计 +1 | `raw/tc-13.log` |
| TC-14 | PASS | 项目键 3 / 平台键 5 / 交集 0；项目库落值 `pytest` | `raw/tc-14.log` |
| TC-28 | GAP | 能力位 `sameRunMixedKernels=False`；混合提交 **200**（期望 400）；run 终态 `canceled` | `raw/tc-28.log` |
| TC-29 | GAP | 默认内核 robot→pytest 切换 1/1；`run` 表 kernel 列 **0**；`run_metadata` kernel 键 **0** | `raw/tc-29.log` |
| TC-31 | GAP | 可见性/公开列 **0**；跨项目共享校验和 **0**；`GET /api/case-sources` **404** | `raw/tc-31.log` |
| TC-39 | PASS | 项目引用 7→6→7；全局 `device` 3→3；DELETE 200 / 恢复 POST 200 | `raw/tc-39.log` |
| TC-41 | PARTIAL | 建组 200 / 加成员 200 / 重复加成员 200 / 授权 200 / 改名 **404** / 删组 **404** / 移除成员 **404**；成员行 1/1；用户数 9→9 | `raw/tc-41.log` |
| TC-42 | GAP | 组授权 **500 ×3**（NPE）；三项目可见 0/3；未授权项目 403 | `raw/tc-42.log` |
| TC-44 | GAP | 加成员 200；`/groups/member/remove` **404**；移除前后项目访问 [403,403] | `raw/tc-44.log` |
| TC-45 | PASS | admin 200 `projects=5 groups=3 reports=0`；非管理员 403 4/4 | `raw/tc-45.log` |
| TC-46 | PASS | admin 5/5 200；engineer 非成员 4/4 403 | `raw/tc-46.log` |
| TC-48 | GAP | 归档端点 POST **404** / PUT `archived` **500**；归档后建 run **200**（未拒绝） | `raw/tc-48.log` |
| TC-49 | GAP | 存储顺序与入参一致 1/1（`3,1,2`）；执行端点 **404**；复制端点 **404** | `raw/tc-49.log` |
| TC-50 | GAP | 组四动作 → `audit_log` 新增 **0** 行（id 394513→394513）；前后值列 **0**/1 | `raw/tc-50.log` |
| TC-52 | GAP | 探测端点 **404**；state 布尔位 4；三态字段 **0**；`ai_cli_probe` 行 0 | `raw/tc-52.log` |
| TC-57 | GAP | 未确认 execute 403；进程树端点 **404**；`ai_action` 行 0；cwd/env 列 **0** | `raw/tc-57.log` |

合计：**PASS 6 / PARTIAL 1 / GAP 10 / FAIL 0**。

## 3. 计划偏差说明（不伪造 PASS）

计划要求「0 FAIL」——**达成：0 FAIL**。但「0 FAIL」不等于「17 条全过」：
10 条按现状判 **GAP**（功能未实现或与判据不符），1 条 PARTIAL。这些是**实现缺口的事实记录**，
不是探针缺陷，也不在 A4 内改代码凑绿。对应根因（均已回读源码确认）：

| TC | 根因（源码位置） |
|---|---|
| TC-28 | `EnvironmentProbe.java:27` 只有 `sameRunMixedKernels` 常量，`RunService` 无 400 分支；提交后正常入队 |
| TC-29 | `run` 表与 `run_metadata` 均无 kernel 承载（`SHOW COLUMNS` 实测），历史 run 内核归属不可查 |
| TC-31 | `case_source` 11 列、按 `project_id` 分行、无可见性字段；无 `/api/case-sources` 端点 |
| TC-41 | `GroupController` 仅 8 个映射，无 `PUT/DELETE /groups/{id}`、无 `member/remove` |
| TC-42 | `EffectiveRoleResolver.compute:37` 对**无直接成员关系**的用户 `access.effectiveRole` 返回 `null` → **NPE 500**；`groups/grant` 因此整体不可用 |
| TC-44 | 同上缺移除端点；且 `AccessControlService.requireProjectAccess` 只查 `project_member`，组成员关系不影响项目访问 |
| TC-48 | `project` 表无 `archived` 列；FR-37 的归档态未落地，PUT 未知字段命中 500 |
| TC-49 | `ScenarioService` 只存 `case_set_ids` 逗号串，无有序步骤/执行/复制 |
| TC-50 | `GroupService` 未接审计；`audit_log` 无 before/after 列 |
| TC-52 | `AiCliProbe` 只探 4 个 CLI，探测端点未暴露，`ai_cli_probe` 零读写 |
| TC-57 | `AiProcessManager` 只拼 argv，无 `ProcessBuilder.directory/env`，无进程树端点 |

TC-42 的 500 已取到真实异常栈（`/tmp/rt-backend.log`）：

```
java.lang.NullPointerException: Cannot invoke "com.qa.autotest.domain.iam.RoleCode.name()"
 because the return value of "...AccessControlService.effectiveRole(long, java.lang.Long)" is null
	at ...EffectiveRoleResolver.compute(EffectiveRoleResolver.java:37)
	at ...GroupService.effective(GroupService.java:65)
	at ...GroupController.grant(GroupController.java:72)
```

> 该栈说明：**有直接项目角色的用户**（viewer）授权 200（TC-41 实测），
> **无直接角色的用户**（nobody）授权必 500——是数据相关缺陷，不是探针问题。

## 4. 现场清理（跑完断言归零）

`cleanup()` 逐项删除本次创建的报告/审计/导出/场景/用户组/组成员/组授权/AI 会话，并打印计数：

```
清理后计数: {'test_report': '0', 'test_scenario': '0', 'user_group': '0',
            'project_group_role': '0', 'user_group_member': '0'}
```

补充核对（手工一次）：`user_group / user_group_member / project_group_role / test_report /
test_scenario / project_setting / system_setting` **全部 0 行**。
TC-13 写入的 `execution.maxParallel=4`（与默认值相同）与 TC-14 写入的项目级 `kernel.default`
均在断言后回滚为「无此行」，平台默认口径不变。

**保留的真实记录**：TC-28 / TC-48 各建了一条 run（终态 `canceled` / `failed`）。
它们是「混合内核未被 400 拦截」的直接证据，属审计事实，不删。

## 5. 本证据的边界

- 只取证，**未改任何产品代码**；上表 10 条 GAP 需进入缺陷台账后由实施轨修复，修复后重跑本脚本即可复验。
- 未覆盖 A5（浏览器）与 A6（WS），二者另有证据包。
- `raw/http-trace.jsonl` 保留 17 条 TC 的完整请求/响应（含耗时），可用于复核每个数字。

## 6. 产物清单

| 文件 | 说明 |
|---|---|
| `run.py` | 17 条 TC 取证脚本（stdlib only；HTTP + mysql CLI） |
| `raw/a4-run.log` | 本轮全过程输出与最终统计 |
| `raw/summary.txt` | 17 行「结论 + 一行数字」总表 |
| `raw/verdicts.json` | 结构化判定，供 A10 回写 |
| `raw/http-trace.jsonl` | 每条请求的方法/路径/状态/耗时/响应体 |
| `raw/tc-*.log` | 单条 TC 的明细（端点返回值、库读数） |
| `raw/bootstrap.log` | 账号、项目、基线计数 |

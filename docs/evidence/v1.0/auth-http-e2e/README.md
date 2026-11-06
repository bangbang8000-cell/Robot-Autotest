# 真实 HTTP 全链路验收（IAM-05 / HRD-01 / EDT-05 / EDT-04）

## 这一份补的是什么缺口

这四个包在 WBS 里原来都卡在同一句话上：**「控制器级 MockMvc 已绿，但真实环境 + 真账号的 HTTP 全链路没跑」**。
MockMvc 不经过真实 Tomcat、不经过真实 CORS/过滤器链、不经过真实登录签发令牌，
也不经过 8001 入口的 `/api` 前缀转发 —— 这些恰好是权限边界最容易漏的地方。

本脚本把同一批端点打成**真实 HTTP**：

```
client → http://127.0.0.1:8001 (统一入口，静态 + /api 反代) → http://127.0.0.1:8081 (Spring Boot) → MySQL 8.0.46
```

登录拿真令牌（`POST /api/auth/login`），带 `Authorization: Bearer` 逐个调用，判状态码 + 判响应体关键字段。

## 复跑

```bash
cd ~/robot-autotest
python3 docs/evidence/v0.3/../v1.0/auth-http-e2e/run.py        # 即本目录 run.py
# 需要 8001/8081 在跑（systemctl --user status robot-autotest-backend robot-autotest-entry）
```

前置：夹具项目与 5 个账号已就位（脚本不自己建，避免污染项目表）。

## 夹具

| 项 | 值 |
|---|---|
| 项目 | `qa-e2e-fixture`（`project.id = 29`） |
| 账号 | `qa-e2e-viewer` / `qa-e2e-engineer` / `qa-e2e-pm` / `qa-e2e-admin` / `qa-e2e-nobody` |
| 口令 | 取本机 `~/.config/robot-autotest/env` 的 `QA_E2E_PASSWORD`（**不入库**，五个账号相同）；PBKDF2-SHA256 12 万轮写入 `user.password_hash` |
| 数据库 | `robot_dev`（MySQL 8.0.46） |

口令哈希格式与后端 `Pbkdf2PasswordEncoder` 一致，所以是**真登录**而不是伪造会话：

```
pbkdf2$sha256$120000$<base64 salt>$<base64 derived>
```

## 结果

**36 / 36 通过**，逐条见 `run.log`。连跑两轮结果相同（幂等）。

| 分组 | 条数 | 覆盖要点 |
|---|---|---|
| `IAM05-HTTP-01..20` | 20 | 未认证 401 / 访客只读 / 访客建用例与设备 403 / 工程师建用例 200 但删设备 403 / 工程师管成员 403 / PM 管成员 200 但建项目 403 / 管理员跨项目读 200 / 非成员读别的项目 403（设备、用例、文件工作区三条）/ **存在与不存在的项目同 403 同响应体**（防枚举）/ 无角色用户列表空集 / `X-Project-Id` 非数字 400 |
| `EDT05-HTTP-01..04` | 4 | `GET /api/editor/permissions?projectId=` 真值断言：访客 `canRead=true, canWrite=false`；工程师 `canWrite=true`；写文件工作区 403 vs 200 |
| `EDT04-KW-01..05b` | 6 | `V2_5` 迁移在真实库 `flyway version=2.5`；种子 **29 条**（平台级 29 / BuiltIn 18）；访客可检索；**工程师写平台级词条 403、管理员 200** |
| `HRD-01` | （并入 IAM05 全组） | 权限矩阵「角色 × 资源 × 动作」在真实链路上的抽样复核，见下面边界说明 |

## 幂等是刻意设计的（踩了三次才稳）

第一版跑第二遍会红，原因是脚本自己改了现场：

1. PM 把 `nobody` 加成 VIEWER 后没撤销 → 第二轮「无角色用户」两条断言失效。
   修法：加 `IAM05-HTTP-11b`，断言完立刻删成员。
2. 工程师建用例用了固定 `TC-E2E-E1` → 第二轮撞唯一键 409。
   修法：改用 `TC-E2E-{uuid8}` 并即时删除。
3. 管理员建平台级词条同理 → 加 `EDT04-KW-05b` 即时删除。

**这条经验比结果本身重要**：验收脚本如果不能重复跑，它记录的「PASS」就只是一次快照，
下一轮回归时没人敢信。

## 边界说明（不夸大）

- **HRD-01 的 112 行权限矩阵没有在这里全量重跑**。矩阵的完整校验仍是
  `AuthzMatrixTest`（113 用例，控制器级）+ `docs/evidence/v1.0/authz-matrix.csv`。
  本脚本在真实链路上复核的是其中最容易被中间件差异影响的子集（跨项目、防枚举、
  角色边界、无角色四类，共 20 条）。两者合起来才算 HRD-01 完整，单看本文件不够。
- 只覆盖**读写权限**，不覆盖并发与事务边界（那属 EXE-02 / EXE-04）。
- 只跑了 viewer / engineer / pm / admin / nobody 五种身份，没有多租户嵌套场景。

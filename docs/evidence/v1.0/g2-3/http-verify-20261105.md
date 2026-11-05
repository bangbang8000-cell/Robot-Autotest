# G2-3 / G2-4 本机 HTTP 实测原始记录（2026-11-05）

> 环境：WSL2 Ubuntu-24.04，后端 `java -jar` :8081（jar 构建于 2026-11-05 23:52:04，含 EXEC-15），
> 库 `robot_dev`（已迁至 `V7_5`）。账号 `qa-ui`，口令从 `~/.config/robot-autotest/qa-ui-pwd` 读取，**不落盘**。
> 性质：**本机接口级实测**。L2 / LX 的请求在执行派发**之前**就被拒，因此不需要真实 DUT。
> 复跑：`bash docs/evidence/v1.0/g2-3/http-verify.sh`；本次输出即下方代码块（token 已截断，其余为原文）。

```
## 0. 环境
时间: 2026-11-05T23:58:37+08:00
后端: http://127.0.0.1:8081 （jar 构建于 2026-11-05 23:52:04.610241233 +0800）
数据库: robot_dev（已迁到 V7_5）

## 1. 登录 POST /api/auth/login
{"code": 0, "codeName": "OK", "message": "OK", "data": {"token": "b05ypAC_…（已截断）", "expiresAt": "2026-11-05T23:58:37.448389814Z", "user": {"id": 2, "username": "qa-ui", "displayName": "UI 走查账号", "email": null, "orgId": null, "enabled": true, "passwordExpiresAt": null, "lastLoginAt": "2026-11-05T15:58:24.483Z", "roles": ["admin"]}, "passwordExpired": false}, "success": true}

## 2. 列表已返回 safetyLevel  GET /api/projects/1/cases?page=1&size=2
{"id": 226, "caseCode": "TN-IMP-226", "title": "REL（可靠性）", "reviewStatus": "approved", "safetyLevel": "LX"}
取样用例: id=226 code=TN-IMP-226

## 3. 人工补判  POST /api/projects/1/cases/batch  {action:setSafetyLevel, safetyLevel:L2}
{"code":0,"codeName":"OK","message":"OK","data":{"total":1,"succeeded":1,"failed":0,"items":[{"id":226,"ok":true,"code":null,"message":null}]},"success":true}
HTTP=200

## 4. 执行所选（L2）  POST /api/execution/cases/run  —— 期望 403
{"code":40301,"codeName":"SAFETY_LEVEL_BLOCKED","message":"以下用例安全级禁止自动执行（L2 高危 / LX 未判定按 L2 对待）：TN-IMP-226(L2)","data":null,"success":false}
HTTP=403

## 5. 非法安全级 L9  —— 期望 400
{"code":40001,"codeName":"INVALID_ARGUMENT","message":"非法安全级: L9（只允许 L0/L1/L2/LX）","data":null,"success":false}
HTTP=400

## 6. 还原 LX 后再执行  —— 期望同样 403（LX 按 L2 对待）
{"code":40301,"codeName":"SAFETY_LEVEL_BLOCKED","message":"以下用例安全级禁止自动执行（L2 高危 / LX 未判定按 L2 对待）：TN-IMP-226(LX)","data":null,"success":false}
HTTP=403

## 7. G2-4a 弱口令被拒（短）  POST /api/users/3/reset-password
{"code":40001,"codeName":"INVALID_ARGUMENT","message":"口令长度至少 10 位","data":null,"success":false}
HTTP=400

## 8. G2-4a 弱口令被拒（无大写）
{"code":40001,"codeName":"INVALID_ARGUMENT","message":"口令必须包含大写字母","data":null,"success":false}
HTTP=400

## 9. G2-4b 会话续期  POST /api/auth/renew
登录时 expiresAt=2026-11-05T23:58:37.448389814Z（TTL=8h，服务端时间口径）
{"code":0,"codeName":"OK","message":"OK","data":{"expiresAt":"2026-11-05T23:58:37.985555520Z"},"success":true}
HTTP=200

## 10. 收尾：取样用例已还原
{"id": 226, "caseCode": "TN-IMP-226", "safetyLevel": "LX"}

（原始记录结束）
```

## 判定

| 项 | 期望 | 实测读数 | 结论 |
|---|---|---|---|
| `L2` 执行所选 | 403 `SAFETY_LEVEL_BLOCKED` | `HTTP=403`，`code=40301`，消息含 `TN-IMP-226(L2)` | PASS |
| `LX` 执行所选 | 403（LX 按 L2 对待） | `HTTP=403`，`code=40301`，消息含 `TN-IMP-226(LX)` | PASS |
| 非法安全级 `L9` | 400 `INVALID_ARGUMENT` | `HTTP=400`，「非法安全级: L9（只允许 L0/L1/L2/LX）」 | PASS |
| 人工补判 `setSafetyLevel` | 200 且落库 | `HTTP=200`，`succeeded=1`；库内查得 `L2`，收尾还原 `LX` | PASS |
| 列表回溯安全级 | 字段存在 | `safetyLevel: "LX"` | PASS |
| G2-4a 弱口令（过短） | 400 | `HTTP=400`，「口令长度至少 10 位」 | PASS |
| G2-4a 弱口令（无大写） | 400 | `HTTP=400`，「口令必须包含大写字母」 | PASS |
| G2-4b 会话续期 | 200 且 `expiresAt` 前推 | `HTTP=200`；`expiresAt` 由 `23:58:37.448Z` 推到 `23:58:37.986Z`（同一次调用内向前重算 8h 窗口） | PASS |

> 关于 `expiresAt` 的口径：会话 TTL = 8h（`SESSION_TTL`），服务端按 `Instant.now() + 8h` 计算；
> 登录返回 `23:58:37.448Z` 与真实 UTC `15:58:37Z` 相差正好 8h，属**预期**而非时区错乱——
> 对照同一响应里的 `lastLoginAt`（真 UTC `15:58:24.483Z`）即可确认。

## 边界

- 这是**本机接口级**证据：证明接口真的回 403/400，且拒绝发生在派发前。
- 不替代现场证据：现场仍需按 G2-3 判据真实触发一次 `L2` 用例并留存拒绝记录（现场后端版本、库、账号各留一份读数）。

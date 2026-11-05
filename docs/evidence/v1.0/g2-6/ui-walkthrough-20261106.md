# G2-6 / G2-4 界面走查（2026-11-06，本机 dev 联调）

> 目标：把「按技术域筛出用例与设备」从代码评审推进到**真实界面 + 真实接口**的读数，
> 同时补上 G2-4「前端 5 分钟定时续」这一项此前只有后端证据的缺口。
> 边界：本机 dev 环境（`robot_dev` 库、vite dev server），**不替代现场走查与签字**；
> 现场要验的是同一条路径在真实台账/真实设备上的表现。

## 1. 环境

| 项 | 值 |
|---|---|
| 后端 | dev 后端 `http://127.0.0.1:8081`，jar 构建于 2026-11-05 23:52:04（含 EXEC-15），库 `robot_dev` |
| 前端 | `npx vite --port 5173` 起，因 5173 被占用回落 **5174**（`http://localhost:5174`），Windows 侧经 WSL localhost 转发访问 |
| 项目 | `demo · 演示项目`（project_id=1，226 条用例 / 3 台设备）；另有 `AST05-B · AST-05 共享演示`（0 条用例） |
| 账号 | `qa-ui`（admin）；口令从 `~/.config/robot-autotest/qa-ui-pwd` 读取，未落盘、未进截图 |

## 2. 数据前置（本次走查暴露的真实缺口）

**台账里没有「技术域」列。** 226 行样例台账（`fixtures/case-ledger/ledger-sample-226.xlsx`）的列是：

```
测试编号 / 一级分类 / 二级分类 / 测试用例 / 测试类型 / 预设条件 / 测试步骤 /
A端命令 / B端命令 / 交换机配置 / 预期结果 / 执行状态与证据 / 当前环境适配 / 执行顺序
```

导入器本身支持 `技术域/domain` 别名映射，但源表无此列，因此**落库后 226 条用例的 `domain_tag` 全为空串**。
后果：G2-6 要验的「按技术域筛出用例」在真实数据上无法产生任何结果，必须先补标注。

本次为走查临时标注了一个可回滚的子集（写入的是 dev 库，非 IT 库）：

```
LOGIN 200 roles= ['admin']
PAGE 200 total= 226 returned= 20
BEFORE first 5: [(226, 'TN-IMP-226', ''), (225, 'TN-IMP-225', ''), (224, 'TN-IMP-224', ''), (223, 'TN-IMP-223', ''), (222, 'TN-IMP-222', '')]
roce_ids [226, 225, 224, 223, 222, 221, 220, 219, 218, 217]
storage_ids [216, 215, 214, 213, 212]
BATCH setDomainTag=roce 200 total= 10 succeeded= 10 failed= 0
BATCH setDomainTag=storage 200 total= 5 succeeded= 5 failed= 0
FILTER domain='roce' 200 total= 10
FILTER domain='storage' 200 total= 5
FILTER domain='no-such-domain' 200 total= 0
FILTER domain='' 200 total= 226
DEVICE-DOMAINS 200 ['roce']
DEVICES domain=roce 200 total= 3 [('leaf-switch-01', 'roce'), ('gpu-node-02', 'roce'), ('gpu-node-01', 'roce')]
DEVICES domain=storage 200 total= 0
```

脚本见同目录 `prep-domain.py`（复跑会把同一批用例重标一次，幂等）。

## 3. 浏览器走查读数

### 3.1 用例页（`/cases`）

| 动作 | 读数 |
|---|---|
| 登录 → `/cases` | 未被踢回登录页；页面标题「用例」 |
| **默认项目** | `AST05-B · AST-05 共享演示`，用例数 **0**（表格「暂无数据」）→ 需手动切到 `demo · 演示项目` |
| 技术域填 `roce` 回车 | 表格 **10 行**、分页仅 1 页，首行 `TN-IMP-226 REL（可靠性）`；编号 TN-IMP-217~226，**不含 TN-IMP-216** |
| 清空技术域回车 | 分页回到 23 页；末页 6 行 → 22×10+6 = **226** |

截图：`cases-filter-roce.png`、`cases-cleared-all.png`。

### 3.2 设备页（`/assets?tab=devices`）

| 动作 | 读数 |
|---|---|
| 技术域下拉选项 | 只有 `全部技术域`、`roce` —— 来自 `GET /api/projects/1/device-domains`，不是前端硬编码 |
| 选 `roce` | 3 行：`leaf-switch-01`、`gpu-node-02`、`gpu-node-01`，技术域列均为 `roce` |
| 过滤是否下发后端 | 网络请求 `GET /api/projects/1/devices?domain=roce&page=1&size=10`（未过滤时无 `domain`） |

截图：`assets-roce-3rows.png`。

### 3.3 刷新后的状态（同时是 G2-4 的前置）

整页重新导航到 `/assets?tab=devices` 后：**仍在页内、未跳 `/login`、无密码框**；
`localStorage` 中 `auth_token` / `auth_expires_at` / `current_project_id` 均存在；
项目仍是 `demo · 演示项目`；技术域筛选**重置**为「全部技术域」（筛选条件不持久化，属现状，不在本包验收范围）。

## 4. G2-4 前端 5 分钟定时续：为什么改成假时钟用例

服务端 `SESSION_TTL = 8h`，`armRenewal()` 排的是「过期前 5 分钟」的定时器 ——
在真实浏览器里要等 **7 小时 55 分**才会触发第一次续期，界面走查无法观察到。
此前该路径只有后端单测（`AuthServiceTest`）与接口实测（`POST /api/auth/renew` → 200）。

本次补 `frontend/src/stores/auth.spec.ts`（新增 4 条，假时钟 + 假 `authApi`），把不可等待的定时行为变成可确定复现：

| 用例 | 断言 |
|---|---|
| 到期前 5 分钟触发续期，回写新过期时间并重排下一次 | 差 1ms 不发请求；到点 `renew` 调用 1 次；`auth_expires_at` 更新为新值；再走 8h-5min 触发第 2 次 |
| 刷新页面后 `loadMe` 会按持久化的过期时间重排定时器 | `loadMe()` 后无需再手工 arm，到点仍触发续期（对应 §3.3 的浏览器现象） |
| 续期失败时清空本地会话 | `renew` 拒绝 → `token` 置空、`auth_token` / `auth_expires_at` 从 localStorage 移除 |
| 没有令牌时不排期 | 推进 8h，`renew` 未被调用 |

实测：`npx vitest run src/stores/auth.spec.ts` → **4 passed**；
全量前端测试 **27 个文件 / 189 条通过**（此前 26 / 185），`npm run build` 通过。

> 写这条用例时踩到一个真实的边界：若把 `renew` 的返回值写死成同一个 `expiresAt`，
> 重排算出的 `due = expiresAt - 5min - now` 会变成负数，`setTimeout` 以 0ms 自激，
> 单测里表现为 `renew` 被调用数万次。服务端每次返回「当时 + 8h」不会出现该问题，
> 但**若将来服务端改成固定过期时间，前端会自激**——已在用例里留注释。

## 5. 判定

| 检查项 | 结论 | 依据 |
|---|---|---|
| 用例按技术域筛选 | **通过（本机界面级）** | `roce` → 10 行 / 清空 → 226 行；与接口 `total` 一致 |
| 设备按技术域筛选 | **通过（本机界面级）** | 下拉选项来自 `device-domains`；`domain=roce` 下发后端并回 3 行 |
| 刷新后登录态保持 | **通过** | 整页重载未跳登录页，localStorage 三项齐全，`loadMe` 重排定时器有单测覆盖 |
| 会话到期前 5 分钟自动续期 | **通过（单测）** | `auth.spec.ts` 4 条；接口侧 200 见 `g2-3/http-verify-20261105.md` |
| 台账技术域数据可用性 | **不通过** | 源表无「技术域」列，226 条 `domain_tag` 全空，需补列或补标 |

## 6. 残余（不由本机走查关闭）

1. **现场走查与签字**：真实台账 + 真实设备上按同一路径复跑一遍，留截图与读数，由人确认后把 G2-6 改「已完成」。
2. **技术域数据来源需定口径**：是给台账加「技术域」列（改 `fixtures` 与导入模板），还是按「一级分类」规则映射，或人工在平台内批量标注。当前 226 条全空，本包在真实数据上等价于「无筛选结果」。
3. 观察项（不影响判定，记录备查）：
   - 首次进入 `/cases` 默认项目是 `AST-05-B`（0 条用例），用户会先看到空表；
   - 技术域筛选条件不随刷新/URL 保留（设备页、用例页一致）。

## 7. 复跑方法

```bash
# 1) 后端（:8081）
cd ~/robot-autotest/backend && mvn -B -q -DskipTests package
source ~/.config/robot-autotest/env
java -jar target/robot-autotest-backend-0.1.0-SNAPSHOT.jar --planning.scheduler-enabled=true &

# 2) 前端（5173 被占则自动落到 5174，端口以启动日志为准）
cd ~/robot-autotest/frontend && npx vite --port 5173 --host 0.0.0.0

# 3) 数据准备 + 接口读数（幂等）
cd ~/robot-autotest && python3 docs/evidence/v1.0/g2-6/prep-domain.py

# 4) 前端定时续单测
cd ~/robot-autotest/frontend && npx vitest run src/stores/auth.spec.ts

# 5) 浏览器（Windows 侧）
#    http://localhost:5174/login → qa-ui → 切项目到「演示项目」→ /cases 填 roce → /assets 选 roce
```

注意：

- 用例页「技术域」输入框**回车**才触发查询（`@press-enter`），只填不回车不会刷新列表。
- 单个用例接口返回 `data.testCase`，列表接口返回 `data.items`，别混用。

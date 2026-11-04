# PRJ-01 项目中心（成员管理）证据包

日期：2026-11-04　·　WBS #67　·　状态：**待验**（`待验 → 已完成` 由人裁定）

## 一、环境与入口

| 项 | 值 |
|---|---|
| 运行环境 | WSL2 Ubuntu-24.04（权威副本 `~/robot-autotest`） |
| 后端 | `java -jar target/robot-autotest-backend-0.1.0-SNAPSHOT.jar --server.port=8081`，`/api/_ping` → `{"status":"UP"}` |
| 前端 | `vite` dev :5273，HTTP 200 |
| 数据库 | `robot_dev`（MySQL 127.0.0.1:3306） |
| 走查账号 | `qa-ui / UI 走查账号`（项目 `demo` 的 owner） |

## 二、改了什么

| 层 | 文件 | 改动 |
|---|---|---|
| 后端 | `domain/iam/ProjectMember.java` | 增 `displayName` / `enabled` 两个字段（成员表要能标「已停用」、显示人名而不是登录名） |
| 后端 | `infrastructure/ProjectRepositoryJdbc.java` | 2 处 SELECT 连 `app_user` 取 `display_name` / `status` |
| 后端 | `application/iam/ProjectService.java` | owner 保护：移除 / 降级 → **409 CONFLICT**（原为 400，语义不对）；类注释写明权限双轨口径 |
| 后端测试 | `api/iam/AuthorizationBoundaryTest.java` | 新增「维度五：owner 保护」4 条（owner 移除/降级 409 + 普通成员增改删 200 对照） |
| 前端 | `api/iam.ts` | `ProjectMember` 类型 + `members/addMember/updateMember/removeMember` |
| 前端 | `views/AdminView.vue` | 新增「项目成员」区块：选项目 → 添加成员 → 表格（改角色 / 移除 / 所有者锁定提示） |
| 前端 | `components/ProjectPicker.vue` | 无项目时给「联系管理员分配成员身份」引导 |
| 前端 | `locales/{zh-CN,en-US}.ts` | 成员区块与空态文案（中英同步） |
| 样式 | `styles/main.css` | `.member-role`（角色下拉与「所有者」标记同行留间距） |

## 三、接口实证（`prj01-api.log`）

项目 `demo`（id=1），owner = `qa-ui`（userId=2）：

| 调用 | 期望 | 实测 |
|---|---|---|
| `GET /api/projects/1/members` | 含 `displayName` / `enabled` | **200**，三条：admin(admin)、qa-ui(admin)、prj01-demo(engineer) |
| `DELETE /api/projects/1/members/2`（owner） | 409 | **409**，`不能移除项目所有者；如需变更请先转移所有权` |
| `PUT /api/projects/1/members/2 {roleCode:engineer}`（降级 owner） | 409 | **409**，`不能降级项目所有者；如需变更请先转移所有权` |
| `POST /api/projects/1/members {userId:3, roleCode:engineer}` | 200 | **200** |
| `PUT /api/projects/1/members/3 {roleCode:viewer}` | 200 | **200** |
| `DELETE /api/projects/1/members/3` | 200 | **200** |

> 对照设计：owner 两条走 409，普通成员三条走 200 —— 证明保护是「精确指向 owner」，不是把成员操作整体关死。

## 四、界面截图

| 文件 | 内容 |
|---|---|
| `01-admin-members-light.png` | 亮色：管理页「项目成员」区块，三条成员、role 下拉、`qa-ui` 行显示 `管理员 · 所有者` 且操作列显示「所有者不可移除」 |
| `02-admin-members-dark.png` | 暗色：同一页面，验证新增区块的语义 token（表头、下拉、危险色「移除」）在暗色下可读 |

截图均为 1920×1080 全页；走查用 `agent-browser`（CDP 驱动本机 Chrome），主题通过 `localStorage.theme` 显式设置后 reload。

> 截图时间点比接口脚本早一步：图中三行成员（含 `prj01-demo` engineer）；接口脚本最后一步把它移除了，
> 所以现在库里是两行（admin、qa-ui）。想复现三行，重新 `POST /api/projects/1/members {userId:3, roleCode:"engineer"}` 即可。

## 五、守门与回归（`prj01-checks.log`）

```
mvn -B test      → Tests run: 463, Failures: 0, Errors: 0（新增 4 条，原 459）
vue-tsc --noEmit → 0 错误
npm run lint     → 通过
npm run build    → ✓ built in 5.02s
check_tokens     → 0 悬空；px 322 处全部在白名单
check_states     → 8 类元素三态齐备
check_contrast   → 4 组 80 项全部达标
check_i18n       → 键集相等 / 无硬编码中文 / 无同值漏译 / 键引用完整
```

## 六、演示库数据的一次性归一（必须知道）

现场发现 `project.owner_id` **为 NULL**，而 `project_member.member_role` 是 V1 基线的 `owner`
（`V1__baseline.sql` 注释写着 `owner/member/viewer`）。`V2_1__iam_fields.sql` 才引入
`project.owner_id`，演示库没有回填 —— 也就是说**owner 保护对本库原本无对象可保护**，
且 `RoleCode.from("owner")` 会抛异常。

本次做了一次性数据归一（仅演示库，非迁移）：

```sql
UPDATE project_member SET member_role='admin' WHERE member_role='owner';  -- 演示库命中 2 行
UPDATE project SET owner_id=2 WHERE id=1 AND owner_id IS NULL;           -- 负责人落到 qa-ui
```

**口径**：`project.owner_id` 是 owner 的唯一真源；`member_role` 取 `admin/pm/engineer/viewer`。
若后续还有环境是 `owner_id IS NULL`，需要按上面同一口径回填，否则保护不生效（不会报错，是静默失效）。

为演示「普通成员可移除」，额外建了演示账号 `prj01-demo`（显示名「PRJ01 演示成员」）并挂在项目 1 下（engineer）。
它留在库里是有意的：下次走查直接能看到三行成员。

## 七、未覆盖（留给人工）

- 无项目引导的**空态截图**：本库当前至少有一个项目，未构造「零项目账号」场景（代码路径已存在）。
- 「停用用户」标记：`enabled=false` 的成员未构造（数据显示为「已停用」的分支未截图）。
- 成员变更的审计记录只在 `audit_log` 落库，未在界面走查中核验。

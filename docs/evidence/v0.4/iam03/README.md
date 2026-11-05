# IAM-03 残余：权限矩阵 192 格全覆盖 + 成员管理前端现状订正

- 日期：2026-11-06
- 对应 WBS：#9 IAM-03（仍保持「待验」）
- 证据：本目录 `permission-matrix-test.log`

## 1. 原缺口（WBS 备注两条）

1. 「权限矩阵『每格有测试』尚未覆盖全量」——原 `PermissionMatrixTest` 只有 6 条抽样用例；
2. 「成员管理仅后端，前端未做成员页」——**该说法已过时**，前端早已有成员管理。

## 2. 本次改动

### 2.1 全量矩阵逐格断言

`PermissionMatrixTest` 新增 `全量矩阵逐格断言`：

- 维度：`RoleCode`（4）× `PermissionResource`（8）× `PermissionAction`（6）= **192 格**，
  逐格 `assertEquals`，失败消息带「角色 + 资源:动作 + 期望允许/拒绝」，能直接指出是哪一格变了；
- 期望值来自**独立于实现的 PRD 抄录表**（`EXPECTED`：每角色 8 个 6 位字符串，
  位序 READ/CREATE/UPDATE/DELETE/EXECUTE/USE，`-` 为拒绝），不是从 `PermissionMatrix` 反推；
- 结尾断言 `checked == 角色数 × 资源数 × 动作数` 且 `== 192`，防止将来枚举扩容或循环被裁剪后
  「看起来通过其实少测了」。

> 口径说明：WBS 原备注写「112 格」，与枚举实际维度（4×8×6=192）不符；本次以代码枚举为准，
> 全量按 192 格断言。192 包含全部「组合」，其中大量为拒绝格——只有拒绝格也被断言，
> 「每格有测试」才成立。

### 2.2 成员管理前端（订正，不改代码）

现状核实（`frontend/src/views/AdminView.vue` + `AdminView.spec.ts`）：

| 能力 | 位置 | 测试 |
|---|---|---|
| 选择项目后拉成员列表 | `AdminView.vue` 项目成员卡片（`projectApi.members`） | `AdminView.spec.ts#AdminView 项目成员` |
| 添加成员（用户 + 角色） | `addMember`，缺用户时给 `admin.memberRequired` 错误 | 同上 |
| 改角色 | 行内 `NSelect` → `updateMember` | 「角色下拉可改」用例 |
| 移除成员（所有者不可删） | `removeMember` + `isOwner` 判断 | 「所有者行无移除按钮」用例 |
| 停用用户显示「已停用」而非「否」 | `statusLabels` 统一口径 | 「停用成员显示已停用」用例 |

结论：**「前端未做成员页」不成立**，WBS 备注已按此订正；本次不为已存在功能重复开发。

## 3. 验证

| 项 | 命令 | 结果 |
|---|---|---|
| 矩阵单测 | `cd backend && mvn -o -Dtest=PermissionMatrixTest -DfailIfNoTests=false test` | `Tests run: 7, Failures: 0, Errors: 0`（`permission-matrix-test.log`） |
| 成员页测试 | `cd frontend && npx vitest run src/views/AdminView.spec.ts` | 全通过（既有用例，未改） |
| 后端全量 | `cd backend && mvn -o test` | 见提交时门禁记录 |

## 4. 未覆盖（明确边界）

- 矩阵只校验**纯函数**口径；接口层「注解 + 服务层」是否与矩阵一致由 `AuthorizationBoundaryTest`
  另行覆盖，两者不互相替代。
- 服务层的「工程师只能读自己」等细粒度规则不在矩阵内（矩阵只管角色 × 资源 × 动作），
  由 `AccessControlService` 的既有用例覆盖。

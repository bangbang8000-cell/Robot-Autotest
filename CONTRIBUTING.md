# 协作规范

本仓库多人协作，`main` 分支受保护。以下规则为强制项，不接受「这次先这样」。

## 一、分支模型

| 分支 | 用途 | 生命周期 |
|---|---|---|
| `main` | 唯一长期分支，随时可交付 | 永久 |
| `feat/<模块>-<简述>` | 新功能 | 合并后删除 |
| `fix/<模块>-<简述>` | 缺陷修复 | 合并后删除 |
| `docs/<简述>` | 纯文档改动 | 合并后删除 |
| `chore/<简述>` | 构建、依赖、CI | 合并后删除 |

模块名取 `backend` / `frontend` / `editor` / `docs` / `infra` 之一。

```bash
git switch -c feat/editor-flow-node-palette
```

**正常流程禁止**直接在 `main` 上提交；`push --force` 与 `reset --hard` 后强推一律禁止。当前账号已列入服务端 bypass 名单，直推 `main` 不会被拒绝，但会跳过评审与 CODEOWNERS，属自担风险操作，仍建议走分支 + PR。

## 二、提交信息

采用 Conventional Commits，中文正文，首行不超过 72 字符：

```
<type>(<scope>): <简述>

<为什么改，不写「改了啥」——改了啥看 diff>
```

`type` 限 `feat|fix|docs|refactor|test|chore|perf`。
`scope` 限 `backend|frontend|editor|docs|infra|deps`。

例：

```
feat(editor): 节点面板支持按关键词标签过滤

原实现一次渲染全部关键词，200+ 项时侧栏卡顿。改为按标签分组后懒加载。
```

## 三、Pull Request

1. 分支从最新 `main` 切出，合并前先 `git rebase main`（保持线性历史）。
2. PR 标题套用提交信息格式，描述按模板填写。
3. **至少 1 人评审通过**方可合并；涉及架构、数据模型、对外接口的改动需 2 人。
4. 合并方式统一 **Squash merge**，保持 `main` 单提交对应单一意图。
5. 评审意见未解决不得合并；「先合再修」不接受。

## 四、代码归属

`.github/CODEOWNERS` 指定关键路径的必审人。改动命中这些路径时，对应 owner 的评审不可跳过：

- `packages/editor/**` —— 自 RoboScope 衍生的编辑器代码，涉及上游补丁边界
- `docs/**` —— 对外交付文档
- `.github/**` —— CI 与协作配置

## 五、硬性工程约束

以下四条来自评估报告，违反会导致返工，评审时直接打回：

1. **用例文本唯一真源**：`.robot` 文件内容即事实，数据库只存索引与快照。禁止出现「数据库改了、文件没改」的双写。
2. **编辑器改动不越界**：`packages/editor` 只做 RF 语义编辑，不得写入业务逻辑（项目、权限、执行调度）。
3. **原始报告必须带 metadata**：六项必填 `CASE_ID` / `RUN_ID` / `QA_PROJECT` / `DUT` / `LINK` / `SW_BUILD`。缺项在 CI 拦截，不靠自觉。
4. **凭据不入库**：密码、token、私钥一律不进仓库。评测环境的凭据通过环境变量或密钥管理注入。

## 六、分支保护

`main` 由 GitHub ruleset **`protect-main`** 服务端强制保护（仓库已转公开，免费版私有库不支持该能力）：

| 规则 | 效果 |
|---|---|
| `pull_request` | 必须走 PR，且需 1 人评审通过；新提交会使旧评审失效；评审讨论须全部已解决 |
| `required_linear_history` | 禁止合并提交，历史保持线性（配合 Squash merge） |
| `non_fast_forward` | 禁止强推 |
| `deletion` | 禁止删除 `main` |
| 绕过 | 仓库管理员（`RepositoryRole` 5）与 bypass 名单内的账号（当前为 `bangbang8000-cell`），用于初始化、紧急回滚与批量同步 |

不在 bypass 名单内的协作者（`write` 权限）**无法**绕过，直推会被服务端拒绝；名单内账号可直推，服务端会记录一条 bypass 审计。

本地另有 `.githooks/pre-push`：直推 `main` 时仅打印提醒、不再阻断（设置 `ALLOW_MAIN_PUSH=1` 可静默提示）。安装：

```bash
git config core.hooksPath .githooks
```

镜像远程（群晖 `origin`）不受钩子拦截，同步命令：

```bash
GIT_SSH_COMMAND="ssh -i ~/.ssh/syno_robot_autotest -o BatchMode=yes" git push origin main
```

## 七、提交前自检

```bash
git status                    # 无意外文件（.docbuild/、target/、node_modules/ 已被忽略）
git diff --staged             # 逐行看过，无调试代码、无明文凭据
git log --oneline -3          # 提交信息格式正确
```

新增或修改测试用例时，另需确认 `output.xml` 的 metadata 齐全。

## 八、环境

- 开发机：Windows，路径 `E:\workspace\自动化测试\Robot-Autotest`
- 运行环境：Ubuntu 26.04，`192.168.110.149`，Python 3.14 虚拟环境在 `~/rf-stack/.venv`
- 代码托管：GitHub 私有仓（主）+ 群晖裸仓（内网镜像）

跨平台协作，换行符由 `.gitattributes` 统一为 LF，不要手动改。

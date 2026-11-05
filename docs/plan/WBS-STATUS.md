# WBS 状态表（唯一进度真源）

**文档编号** RA-PLAN-2026-001-A ｜ **对应规划** `docs/自动化测试平台中长期工作规划.md` v2.1 ｜ **更新规则** 状态一变即改，不攒着

> **五态**：`未开始` → `进行中` → `待验` → `已完成`，外加一个**旁支态** `待现场`（实现完成，但必须人 / 真机 / 外部系统 / 签字才能验）。
> `待验` 与 `待现场` 都只能由人改成 `已完成`；`已完成` 必须能贴出可复现命令或证据文件路径。
> `待现场` 的定义与适用范围见规划 §6.4；v2.1 新增的 `ENV-01~05` 登记在表末 **#57~61**。

| 汇总 | 未开始 | 进行中 | 待验 | 待现场 | 已完成 |
|---|---:|---:|---:|---:|---:|
| 96 个任务包 | **0** | **0** | **83** | **0** | **13** |

**当前进度：13 / 96（约 13.5% 已完成 · 83 待验 · 0 待现场 · 0 未开始 · 0 进行中）**

> **2026-11-05 状态口径变更**：八个 `待现场` 包按大师「F2 验收通过」裁决转 `已完成`（授权代改，
> 逐包清单见文末修订记录）。这不是 AI 自判 —— 沿用 `ENV-01~05` 的「授权代改」先例，
> 大师口头裁决即为放行依据；若其中某包实际未过，直接改回 `待现场` 并注明即可。

**一句话读法**：功能代码已铺到 v1.0（后端 `Tests run: 547, Failures: 0`、`-Pit` 集成 **30/30**（`SchemaIT` 5 + `CaseImportIT` 1 + `CaseBatchIT` 3 + 其余 21），**应用真启动通过**（构建 13.6s + 启动 4.5s），**执行域本机已真跑通一条 run**（`RUN-20261104155814-952426` → `FINISHED 2/2` → 报告入库），**人工触发链路 2026-11-05 接通**（quick-run / 用例集立即执行 / CI 端点均落派发器），前端 `build`/`lint` exit 0，dev/it 库均已迁到 **V7_5 / 44 表**），但**验收尚未启动**，所以读数是 0%。**这不是烂尾，是验收产能没建起来**——详见 `docs/plan/开发计划评估与后续推进-20261103.md`。

| 分组 | 包数 | 现状 | 下一步 |
|---|---:|---|---|
| **进行中** | 0 | **2026-11-05 清零**：`UX-06`、`EXEC-15` 均落库并直接转 `待验`，全部 96 个包没有 `未开始`/`进行中` | 进入 F1（G1→G2 成组审）；本表已无待认领包 |
| **待现场**（需人 / 真机 / 授权 / 签字） | 0 | **2026-11-05 清零**：8 个包按大师「F2 验收通过」转 `已完成`（授权代改） | 新的现场项出现时再登记，不再攒批 |
| **待验·需环境** | 0 | **环境类阻断 2026-11-04 已清零**：DB 口令重置（`robot_it` → **V7_1 / 44 表**，`db-audit` 7 项全 PASS，`SchemaIT` **5/5 通过**），CAS-03 / WS-04 / WS-05 全部解锁 | — |
| **待验·本地可验** | 83 | 本轮新增 `EXEC-15`（执行安全级闸门，G2-3）；其余同前 | 按 **G1 → G2** 成组审（F1） |

**验收顺序（2026-11-04 大师定）**：**门禁优先 G1→G2**，不按 WBS 编号逐包审。
**已知口径变更**：
① `CAS-03` 的**条数口径未定**：计划书原写 461 条，在手样本为 **226 条**（`TN-IMP-001~226`，大师 2026-11-04 定「先不定，只当样本」）；导入对账先按 226 跑通，**不得据 226 的数字置 `已完成`**，等全集口径明确；
② **开发期禁跑令（ADR-0013）**：G5 通过前禁止在真实 DUT/交换机执行台账用例；`CAS-03` 只做导入不做执行；验证程序一律用合成用例 + L0（实测 226 条中 L0 仅 10 条）；
③ 数据库实际为 **MySQL 8.0**，MariaDB 列为待验（见 ADR-0002 修订）；
④ 24h 压测已取消（见 ADR-0012）；
⑤ **台账 4 列命令类信息确认丢失**（大师 2026-11-04 定「补别名，接受命令列丢失」）：表头对账 `6/14` 通过，但「测试步骤 / A端命令 / B端命令 / 交换机配置」不可入库；且台账无优先级列 → 226 条全部落 `P2`。**这是定案，不再作为 CAS-03 的返工项**，除非将来另开「扩展用例模型」需求。

**仍未验证的三类（不可能靠读代码解决）**：真实环境实跑（pabot / 4 并发 / DUT / 打印链路）、需要日历时间的门禁（G6 连续 7 天）、人工判定项（许可、IA、报告能否对外、新人能否按手册操作）。

| # | WBS | 任务 | 批次 | 状态 | 证据 | 阻塞 / 下一步 |
|---:|---|---|---|---|---|---|
| 1 | WS-01 | monorepo 骨架（backend / frontend / packages / docs）+ 构建脚本 | B1 | 待验 | `Makefile`、`scripts/bootstrap.sh`、`scripts/build.sh`；`make bootstrap` / `make build` 均退出 0（2026-11-02 实测） | 待人工合并后改 `已完成` |
| 2 | WS-02 | Spring Boot 骨架 + 分层包结构 + ArchUnit 规则 | B1 | 待验 | `backend/pom.xml`、`src/main/java/com/qa/autotest/**`、`src/test/java/com/qa/autotest/arch/*`；`docs/evidence/v0.1/mvn-test.log`（`Tests run: 13, Failures: 0`，R1~R5 + 无环 6 条规则 + 4 条元测试）；jacoco 报告 `backend/target/site/jacoco/index.html` | 待人工合并；后端静态检查（checkstyle）未接入，计划 DOC-02 补 |
| 3 | WS-03 | Vue 3 + Vite + TS + Pinia + i18n 骨架 | B1 | 待验 | `frontend/package.json`、`vite.config.ts`、`src/router/index.ts`（6 路由 + 404）、`src/locales/*`；`docs/evidence/v0.1/npm-build.log`、`npm-lint.log` 均 exit 0 | 待人工合并；页面仅骨架，内容随 v0.2+ 填充 |
| 4 | WS-04 | MariaDB schema v1（38 表分域）+ Flyway | B1 | 待验 | `db/migration/V1__baseline.sql`（38 表 / 7 域）、`db/down/V1__baseline.down.sql`、`db/README.md`、`scripts/db-*.sh` | **2026-11-04 已实跑（原「未实跑」作废）**：`robot_it` 从零迁移经 `SchemaIT` 覆盖，落到 **V7_1 / 44 表 / 17 迁移**，`db-audit` 7 项全 PASS（证据 `docs/evidence/v0.1/env02-build-test-20261104.log` §二）。**仍待人工确认**：手写 down 迁移是否逐版本可回滚（本次只验了 up） |
| 5 | WS-05 | CI：自托管 runner + PR 流水线（lint / test / build） | B1 | 待验 | `.github/workflows/pr.yml`（lint→test→build 串行、self-hosted `rf-lab`、`paths-ignore`、setup-java/npm 缓存）、`scripts/runner-install.sh`（只装不注册）、`scripts/ci-local.sh` | **需人工在本地 WSL 注册 runner 后开 PR 验三绿**；runner 未注册前用 `make ci-local` 降级 |
| 6 | WS-06 | 环境与配置基线（venv、PATH、时区、产物根、密钥注入） | B1 | 待验 | `docs/env/环境基线.md`、`scripts/env-check.sh`、`make env-check`；`application.yml` 缺 `DB_PASSWORD` 即启动失败 | 待人工按文档在本地 WSL 走一遍（30 分钟验收） |
| 7 | IAM-01 | 用户 / 角色 / 项目 CRUD | B2 | 待验 | `application/iam/{UserService,RoleService,ProjectService}.java`、`api/iam/{UserController,ProjectController}.java`、`infrastructure/persistence/*RepositoryJdbc`；前端 `views/AdminView.vue` 用户/角色/项目列表与新建 | 后端 CRUD 未在真实库跑集成测试；需人工在本地 WSL 验 `mvn -Pit test` |
| 8 | IAM-02 | 登录与会话（本地账号 + 密码策略） | B2 | 待验 | `application/iam/{AuthService,PasswordPolicy}.java`、`common/security/{PasswordHasher,TokenGenerator}.java`、`api/iam/AuthController.java`、`api/AuthInterceptor.java`；前端 `stores/auth.ts` + `views/LoginView.vue` + 路由守卫；`PasswordPolicyTest` 5 条；**2026-11-05 补会话续期**：`POST /api/auth/renew`（`AuthService.renew` + `SessionTokenRepository.extend`）+ `stores/auth.ts` 提前 5 分钟定时续，`AuthServiceRenewTest` 3 条 | 连续 5 次失败锁定与令牌过期需集成测试/手工验证；续期端点本机 HTTP 已实测（`g2-3/http-verify-20261105.md`），前端定时续由 `stores/auth.spec.ts` 4 条假时钟用例覆盖（2026-11-06） |
| 9 | IAM-03 | RBAC + 项目成员 | B2 | 待验 | `domain/iam/PermissionMatrix.java` + `PermissionMatrixTest` 5 条；`application/iam/{AccessControlService,ProjectService}.java`（成员增改删）；接口按 `资源:动作` 校验 | 权限矩阵「每格有测试」尚未覆盖全量；成员管理仅后端，前端未做成员页 |
| 10 | IAM-04 | 租户隔离（行级 `org_id` + 拦截器） | B2 | 待验 | `common/tenant/{TenantContext,TenantGuard}.java`、`TenantGuardMetaTest` 5 条、`docs/adr/0006-tenant-isolation.md`；控制器统一 `access.bindProject(pathProjectId)` | 用「强制守卫 + 元测试」替代 MyBatis 拦截器（ADR-0006）；真实 SQL 覆盖需集成测试 |
| 11 | IAM-05 | 越权测试集（≥20 条） | B2 | 待验 | `backend/src/test/java/com/qa/autotest/api/iam/AuthorizationBoundaryTest.java`（**36 条**控制器级 MockMvc，真实 `AccessControlService` + 伪造 `UserRepository`/`ProjectRepository`）：未认证 7 / 跨项目 10（含防枚举与 `X-Project-Id` 伪造）/ 角色边界 16 / 无角色无成员 3；`docs/evidence/v0.5/iam05-authz.log`（`Tests run: 204, Failures: 0`，主代理复跑核验）、`docs/evidence/v0.5/iam05-authz-20.md` | 仍缺「本地 WSL 真实环境 + 真账号的 HTTP 全链路」；ENGINEER 删用例按 PRD 冻结矩阵为 ALLOW，已在证据中说明 |
| 12 | AST-01 | 设备与端口台账 | B2 | 待验 | `application/asset/DeviceService.java`、`api/asset/DeviceController.java`、`infrastructure/persistence/DeviceRepositoryJdbc`；前端 `views/AssetsView.vue`（设备/端口下钻、序列号唯一校验） | 真实库集成测试待跑 |
| 13 | AST-02 | 环境与拓扑（DUT-LINK-HOST 链） | B2 | 待验 | `application/asset/{LinkService,EnvProfileService}.java`、`api/asset/{LinkController,EnvController}.java`；链路一次查询带两端设备/端口名 | 拓扑节点 UI 未做；实测链路需真实设备数据 |
| 14 | AST-03 | 凭据存储（加密 + 运行时注入不落盘） | B2 | 待验 | `common/security/SecretCipher.java`、`infrastructure/CipherConfig.java`、`application/asset/CredentialService.java`、`CredentialController`；`SecretCipherTest` 3 条；前端凭据页只显示 `configured` | 需人工验 `CRED_MASTER_KEY` 注入与磁盘/日志无明文扫描 |
| 15 | AST-04 | 技术域标签体系与筛选器 | B2 | 待验 | `application/asset/LabelService.java`、`api/asset/LabelController.java`；设备列表按 `domain` 筛选、用例页按技术域筛选；`docs/evidence/v1.0/g2-6/`（2026-11-06 本机界面走查：用例 `roce` → 10 行 / 清空 → 226，设备下拉来自 `/device-domains` 且 `domain=roce` → 3 行，含 3 张截图） | 标签与用例共用标签集需集成测试确认；**台账无「技术域」列，226 条 `domain_tag` 全空**，真实数据上筛选等价于空结果，需先定数据来源（加列 / 按一级分类映射 / 平台内批量标注） |
| 16 | CAS-01 | 双模用例模型（台账 + 脚本）建模与评审 | B2 | 待验 | `docs/adr/0005-dual-mode-case-model.md`、`docs/release/v0.2-ledger/CAS-01-评审纪要.md`；`V2_3__case_fields.sql` | 待人工签字冻结（G2 后改模型需 ADR） |
| 17 | CAS-02 | 用例库 / 用例集 CRUD | B2 | 待验 | `application/caseasset/{TestCaseService,TestSetService}.java`、`api/caseasset/{CaseController,TestSetController}.java`；前端 `views/CasesView.vue`（筛选/新建/提审/审核/删除） | 用例集前端页面未做（仅后端 API） |
| 18 | CAS-03 | 台账导入（字段映射 + 校验 + 回滚） | B2 | 待验 | `application/caseasset/CaseImportService.java`（POI 5.2.5，中英文表头别名、整批事务、行级错误）、`CaseController` preview/commit；前端导入面板。**安全分级已做**：`tools/classify_cases.py` 对 226 条样本产出 L0 10 / L1 89 / L2 32 / LX 95，证据 `docs/evidence/v0.2/cas03-classify.log` + `cas03-ledger-classification.csv`（见 ADR-0013） | **只做导入、不做执行**：`preview` 按 226 条跑通、重导幂等；条数口径待定（461 vs 226），**不得据 226 置 `已完成`**；`CaseImportService` 无执行路径（已核 `robot/exec/ssh/pabot` 0 命中） **2026-11-04 解除阻断（大师定「补别名，接受命令列丢失」）**：`HEADER_ALIASES` 补入「测试编号 / 测试用例 / 一级分类 / 测试类型 / 预置条件」，静态对账从 **1/14 → 6/14 且 `[PASS]`**（证据 `docs/evidence/v0.1/env04-header-check.log`）。三点必须记住：① **测试步骤 / A端命令 / B端命令 / 交换机配置 4 列无处存放，导入时静默丢弃**（`ImportRow` 无对应字段，属定案取舍）；② 台账**无优先级列** → 226 条全部落 `P2`；③ 「执行顺序」（"第二优先：其余70项"）**故意不映射**到 priority，否则 `Priority.from` 抛「非法优先级」把全表判错。**2026-11-04 真跑通过（判据达成）**：新增 `CaseImportIT`（`@Tag("it")`，连真库 `robot_it`）端到端验证 —— `preview totalRows=226 validRows=226 errors=0` → `commit imported=226` → 重导 `validRows=0 errors=226`（逐行「编号已存在于项目」），首末条 `TN-IMP-001` / `TN-IMP-226` 完整。证据 `docs/evidence/v0.2/cas03-import-it.log`。**未覆盖项（勿读成已验）**：不起 Spring 上下文故 `@Transactional` 不生效 → **「整批回滚」未验证**；403 拒绝路径、并发导入未验证。**2026-11-06 补齐**：新增 `CaseImportTxIT`（最小 Spring 上下文 `AnnotationConfigApplicationContext` + `@EnableTransactionManagement` + `DataSourceTransactionManager`，不起 Boot/Web/Flyway，连真库 `robot_it`）——整批回滚（第 3 行撞编号 → `CONFLICT`，前两行不留）、`viewer` 与非成员两条 403、两线程并发（只一批落库、库里恰好 5 行，连跑 3 次一致）；顺带修行并发撞唯一键抛 `DuplicateKeyException` 会落兜底 500 的问题 → `CaseImportService.commit` 内翻译为 `CONFLICT`(409)。证据 `docs/evidence/v1.0/cas03-tx/cas03-tx-it-20261106.md`。仍未覆盖：HTTP 层 403 状态码/响应体、多实例并发规模 |
| 19 | CAS-04 | 文件工作区 6 端点（树/上传/下载/移动/重命名/删除） | B2 | 待验 | `application/caseasset/FileWorkspaceService.java`、`api/caseasset/FileController.java`（tree/content/versions/save/upload/move/delete）、`FilePathTest` 5 条 | 6 端点集成测试待跑；编辑器 UI 属 v0.3 |
| 20 | CAS-05 | 能力索引（关键词能力清单） | B2 | 待验 | `application/caseasset/CapabilityService.java`、`api/caseasset/CaseController#capabilities`；前端用例页能力索引展示 | 按能力反查非空需真实数据验证 |
| 21 | EDT-01 | 依赖闭包抽取（39 文件 / 15,946 行）+ monorepo 化 | B3 | 待验 | `packages/editor/`（40 个上游文件 + 9 个适配缝）、`scripts/closure.mjs`、`UPSTREAM.md`、`NOTICE.md`、`LICENSE-roboscope`；`npm run build` exit 0（82 modules / 403.6 kB / gzip 81 kB）；HTTP 扫描 `docs/evidence/v0.3/http-scan.log` 0 命中 | 需评审认定「api 模块重写为适配缝而非物理删除」这一偏差（理由见 `packages/editor/UPSTREAM.md` §4）；19 个上游测试已于 EDT-06 接入（见 #26） |
| 22 | EDT-02 | 15 个文件改写（去 repoId / HTTP / 宿主 i18n 依赖） | B3 | 待验 | props 收敛为 `content` / `readonly` / `height`，`scopeId` + `filePath` 改由 `setEditorScope()` 注入；`emits` 增 `dirty`；20 个改写文件加 `Modified by Robot-Autotest` 头；扫描 `axios\|fetch(\|XMLHttpRequest\|repoId\|router.push` **0 命中**；`npm run build` exit 0 | 证据 `docs/evidence/v0.3/edt02-scan.log`、`npm-build-edt02.log`；待人工评审「api 适配缝保留上游签名」这一偏差 |
| 23 | EDT-03 | 三视图打通（文本 / 表单 / 流程） | B3 | 待验 | `packages/editor/tests/three-views.spec.ts`（2 用例，`npm run test` 2/2 通过，130 ms）；`docs/evidence/v0.3/vitest-three-views.log`；`robotTextIO.ts` 增 `depth` 修复嵌套块缩进丢失 | 组件级 sha256 断言已绿；E2E（Playwright `e2e/editor-three-views.spec.ts`）待 v0.3 宿主页落地后补 |
| 24 | EDT-04 | 关键词面板接平台（后端提供 keyword 元数据） | B3 | 待验 | 后端：`db/migration/V2_5__editor_keywords.sql`（`editor_keyword` 表，`project_id=0` 平台级 / `>0` 项目私有，种子 30 条含 BuiltIn 18 条）+ `db/down/V2_5__editor_keywords.down.sql`、`domain/editor/EditorKeyword.java`、`application/repository/EditorKeywordRepository.java`、`infrastructure/EditorKeywordRepositoryJdbc.java`、`application/editor/EditorKeywordService.java`、`api/editor/EditorKeywordController.java`（`GET/POST/PUT/DELETE /api/editor/keywords`）、`TenantGuard` 登记新表；编辑器：`flow/keywordArgs.ts`（`placeholderArgs` / `STATIC_KEYWORD_ARGS` / `argsFor`）、`KeywordPalette.vue` 断网降级「暂无数据」+ 插入补必填占位符；测试 `packages/editor/tests/keyword-palette.spec.ts`（5 条，含断网降级与 `Should Be Equal → ${actual} ${expected}`）、后端 `EditorKeywordSanitizeTest`（4 条）；证据 `docs/evidence/v0.3/edt04-vitest.log`（208/208 全绿）、`edt04-mvn-test.log`（`Tests run: 40, Failures: 0`） | 宿主页（`frontend` 消费 `@robot-autotest/editor` 的页面）尚未落地，适配器注入随宿主页接线；`V2_5` 迁移与平台级词条写权限需在本地 WSL 真实库验证。顺带修 `packages/editor` 构建：`tsconfig.build.json` 把类型检查收敛到 `src`（EDT-06 引入的上游测试断言不可改，故不参与 `vue-tsc`） |
| 25 | EDT-05 | 只读查看器 + 权限联动 | B3 | 待验 | 编辑器：`RobotEditor.vue` 三处闸门（`emitContent` 只读直接 return、CodeMirror `EditorState.readOnly` + `EditorView.editable(false)`、`.robot-editor--readonly` 禁用表单控件）；测试 `packages/editor/tests/readonly.spec.ts`（3 条差分：只读 0 条 `update:content`、非只读必须回写并置 dirty）；全量 211/211 `docs/evidence/v0.3/edt05-vitest.log`。后端：`application/editor/EditorPermissionService.java`（`canRead`=CASE READ、`canWrite`=CASE CREATE）、`api/editor/EditorPermissionController.java`（`GET /api/editor/permissions?projectId=`）、`EditorPermissionTest`（4 条，含访客 CREATE/UPDATE/DELETE 全拒）；`docs/evidence/v0.3/edt05-mvn-test.log`（`Tests run: 44, Failures: 0`）。宿主页：`frontend/src/views/EditorView.vue` + 路由 `/editor/:projectId`、`/viewer/:projectId`（强制只读，FR-v0.3-18）、`api/files.ts`、`api/editor.ts#editorPermissionApi`、`types/editor-package.d.ts`、`vite.config.ts` 别名指向 `packages/editor/dist`；`docs/evidence/v0.3/edt05-frontend-build.log`（exit 0，`EditorView` 分块 853.84 kB） | 只读用户的真实 403 与页面禁用需在本地 WSL 连库用真账号验（`PUT /api/projects/{id}/files` 走 `require(CASE, CREATE)`）；编辑器包以 `dist` + 手写 `.d.ts` 接入，换正式 `vite-plugin-dts` 需联网装依赖；保存链路 `/api/editor/save` 归 EDT-09 |
| 26 | EDT-06 | 往返保真（RF-EQUIVALENT）+ 19 个上游测试接入 | B3 | 待验 | `fixtures/roundtrip`（51 样本：13 生产 + 17 legacy + 20 语法边界 + 1 helper resource）；序列化 `packages/editor/tools/serialize-corpus.mts` → `artifacts/roundtrip/serialized`；独立裁判 `tools/roundtrip_judge.py`（L1 `robot --dryrun` 98 通过 / 0 失败 / 2 跳过无用例文件，L2 AST 51/51 一致，判定 PASS）；`docs/evidence/v0.3/roundtrip.md`；19 个上游 spec 字节级未改且 203 用例全绿：`docs/evidence/v0.3/upstream-19.log` | 裁判先证伪：首轮抓到 2 类真语义损坏（`[Teardown]` 续行被并入上一条 `Set Test Message`、多变量 `FOR` 丢 `${item}`）与 5 类纯排版差异，已修实现（`robotTextIO.ts` 的 `lastSetting` / `loopVars`）。剩余待人工复核：`_tokens` 用 `.tokens` 而非 `get_tokens()`（后者传空参数返回空列表，会让 L2 恒真） |
| 27 | EDT-07 | 中文包合并（2,103 行）+ 品牌残留清理（42 处） | B3 | 待验 | 语言包：`packages/editor/tools/extract-locales.mts` 用 esbuild 编译上游 `en.ts`/`zh.ts` 后取 6 个命名空间（`common` 落为 `editor.common`，不与平台共享键），生成 `packages/editor/src/locales/{zh-CN,en-US}.ts`（**447 叶子键，zh/en 集合一致**），宿主 `frontend/src/locales/index.ts` 顶层展开合并；`packages/editor/tests/i18n-locales.spec.ts` 4 条守护（源码 233 个静态键 + 2 个动态前缀全部可解析），`npx vitest run` **215/215 全绿**（`docs/evidence/v0.3/edt07-vitest.log`）；生成器自检见 `edt07-locales.log`。品牌：`scripts/brand-scan.sh` + `brand-allow.txt`（白名单仅 2 类 / 6 条），产品面（`packages/editor/src`+`index.ts`+`dist`、`frontend/src`+`dist`、`backend/src`）命中 71 处**全部白名单放行、违规 0**（`edt07-brand-scan.log`）；真残留已改 4 处 + 2 个 localStorage 键（`robot-autotest.flowPalette.*`）；构建 `edt07-npm-build-editor.log`（452.38 kB / gzip 95.58 kB）、`edt07-frontend-build.log`（首屏 216.67 kB / `EditorView` 857.76 kB，与 EDT-05 的 853.84 kB 持平）、`edt07-frontend-lint.log` 均 exit 0；后端 `edt07-mvn-test.log`（`Tests run: 64, Failures: 0`） | 待人工复核两点：① 白名单两条理由（Apache-2.0 §4(b) 归属声明必须保留、`RoboScopeHeal` 是 RF 库名不可改）；② 上游 `zh.ts` 有 21 条成句英文缺译，由本项目补译（见 `extract-locales.mts` 的 `ZH_GAP_FIXES`），需人眼校一遍措辞。浏览器内中文显示未做人工抽验 |
| 28 | EDT-08 | 许可合规四件事（LICENSE / NOTICE / 文件头 / 品牌） | B3 | 已完成 | 根 `LICENSE`（Apache-2.0）、根 `LICENSE-roboscope`、根 `NOTICE`（四要素：来源/版本、许可、修改声明、商标）；三份许可 sha256 一致 `807A8F9B…314D09`；20 个改写文件逐个带双声明（`docs/evidence/v0.3/edt08-license-check.log`）；发布检查单 `docs/release/v0.3-editor/EDT-08-发布检查单.md`（含人签栏） | **C 级包，待人工签署**：根许可选型（Apache-2.0）与白名单理由需人确认，签署栏未签；签署后由人把状态改 `已完成` **2026-11-05 按大师「F2 验收通过」裁决授权代改**：原文的待现场口径保留以便追溯；如实际未满足，请直接改回 `待现场` 并注明，本行状态不代表 AI 自判。 |
| 29 | EDT-09 | 平台保存链路（内容 → 文件 → 版本） | B3 | 待验 | 后端：`EditorSaveService`（`save` / `rollback` / `diff`，`require(CASE,CREATE)` 先于写库）、`EditorSaveController`（`PUT /api/editor/save`、`POST /api/editor/rollback`、`GET /api/editor/diff`）、`RobotFileRepository#updateContentIfVersion`（`WHERE current_version=?` 乐观锁，0 行即 409）、`LineDiff`（LCS 行级 diff，含超限退化）；语法校验 `ProcessRobotSyntaxValidator`（RF `--dryrun`，命令可配，缺 RF 返回 `checked=false` 不误判通过）。测试 `EditorSaveServiceTest`（9 条：新建 v1、内容未变不增版、三次保存三个版本、版本落后 409、语法失败带行号、未校验放行、sha256 不符拒绝、回滚不删历史、访客 403）+ `LineDiffTest`（6 条）+ `ProcessRobotSyntaxValidatorTest`（5 条）；`Tests run: 64, Failures: 0`（`docs/evidence/v0.3/edt09-mvn-test.log`）。前端：`EditorView.vue` 保存按钮（只读隐藏）/ 版本历史面板 / 对比当前 diff 弹层 / 409 自动刷新提示，`api/editor.ts#editorSaveApi`、`api/files.ts#versions`；`docs/evidence/v0.3/edt09-frontend-build.log`（exit 0，`EditorView` 857.63 kB） | 本机无 RF，`--dryrun` 真实拦截只在本地 WSL 生效（本机已验「缺 RF → `syntaxChecked=false` 不阻断」）；端到端需在本地 WSL 连库跑「保存三次 → 三版本 → 回滚 → diff」；保存链路的真实 403 需真账号 |
| 30 | EXE-01 | 执行域骨架（队列 / worker / 运行状态机） | B4 | 待验 | `domain/execution/{RunState,RunStateMachine,IllegalStateTransition,RunRecord}.java`、`application/execution/{RunService,RunExecutionService,RunQueuePolicy,RunIdGenerator}.java`、`api/execution/RunController.java`；`RunStateMachineTest` 4 条、`RunQueuePolicyTest` 3 条、`RunIdGeneratorTest` 2 条；`docs/evidence/v0.4/mvn-test.log`（`Tests run: 147, Failures: 0`）；前端 `views/RunsView.vue` + `api/execution.ts` | 真实库的队列排序、状态跃迁与并发落库需在本地 WSL 用 `mvn -Pit test` 验证；worker 仍是单线程串行。**2026-11-04 核查新增缺口（做 OVW-01 时发现）**：`POST /api/execution/runs` 只把 run 落成 `queued`，**没有任何队列消费者**（`RunQueuePolicy` 全仓 0 调用点），真正执行的只有执行计划的 `trigger-now` 与定时触发；且 queued run 未持久化 `suitePaths`，重启后无法重建命令 → 手工运行会**永远停在排队中**。这不是总览的缺陷，是 EXE-01 未完成的部分；补齐需先做「命令参数持久化 + worker 拉取」，属独立任务 |
| 31 | EXE-02 | 资源租约表与状态机（设备 + 端口粒度） | B4 | 待验 | `db/migration/V4_1__lease.sql`（含 `resource_lease` / `resource_lease_history`）+ down、`infrastructure/ResourceLeaseRepositoryJdbc.java`、`application/execution/LeaseService.java`、`api/execution/LeaseController.java`；`LeaseConcurrencyTest` 2 条（100 线程同 key 成功恰 1 / 10 key 总成功 10）、`LeaseServiceTest` 5 条；`docs/evidence/v0.4/lease-concurrency.log`；`docs/evidence/v0.4/lease-conflict-0.sql`（首行 `[待本地 WSL执行]`） | **本机并发是内存语义**（`putIfAbsent` 复刻唯一键），MariaDB `uk_resource_lease_key` 的真实并发与回滚需按 `lease-conflict-0.sql` 在本地 WSL 执行 |
| 32 | EXE-03 | pabot 编排（PATH / 双日志 / 退出码校验） | B4 | 待验 | `PabotCommandBuilder`、`PabotOrchestrator`、`ArtifactVerifier`、`ExecutionPrecheck`；`PabotCommandBuilderTest` 4 条、`ArtifactVerifierTest` 5 条、`ExecutionPrecheckTest` 8 条；**真跑证据** `docs/evidence/v0.4/exe03-pabot-2proc.log`（2 进程 8/8 PASS，50.40s→26.75s）、`exe03-fault-injection.log` / `.md`（kill -9 分片 → 退出码 252） | 真跑已完成（2026-11-04，WSL）：① 2 进程 8/8 PASS；② `kill -9` 一个 `bin/robot` 分片 → **退出码 252**、`output.xml` 只剩 6 条 PASS。**发现缺陷**：`ArtifactVerifier:34` 只判 `totalTests > 0`，丢 2 条时 6>0 仍返回 `ok` → **部分丢失被放行（假绿）**；**2026-11-04 假绿缺陷已修**：`ArtifactVerifier` 增 `verify(Path, expectedCaseCount)` → 少条报 `partial_results`、多条报 `unexpected_extra`；增 `checkShards(Path)` 逐分片查 `output.xml`，空分片报 `shard_incomplete`（无分片目录返回 `no_shard_dir` 判 ok，避免误伤单分片与替身）；`PabotOrchestrator:87` 已接入，`success` 现在要求 `exit==0 && 条数够 && 分片齐`。单测从 5 条增到 **11 条**。**同时修掉第二个假绿**：`mvn -Pit test` 因 surefire 默认不含 `*IT.java` 一直 `Tests run: 0 / BUILD SUCCESS`，已加 `**/*IT.java` include，现 `SchemaIT` 真跑 **5/5**  |
| 33 | EXE-04 | 实时日志推送（WebSocket + 限流） | B4 | 待验 | `LogRingBuffer`（2000 行）、`LogBatcher`（100ms / ≤50 行 / 超限省略）、`RunLogService`、`api/execution/{RunLogWebSocketHandler,ExecutionWebSocketConfig}.java`、`RunController#log`；`LogRingBufferTest` 3 条、`LogBatcherTest` 3 条、`RunLogServiceTest` 4 条、`LogDeliveryLatencyTest` 1 条；`docs/evidence/v0.4/log-latency.txt`（本机 40 样本 p95=101 ms）、`frontend-build.log`、`frontend-lint.log` | 真实 WebSocket 端到端、断线重连与 2000 行上限需在本地 WSL 浏览器抽验；`log-latency.txt` 只覆盖服务端 flush，不含网络传输 |
| 34 | EXE-05 | 取消 / 超时 / 幂等 | B4 | 待验 | `CancelService`、`RunTimeoutMonitor`、`RestartRecoveryService`、`ProcessRegistry`、`ProcessTreeKiller`；`CancelServiceTest` 2 条、`RunTimeoutMonitorTest` 3 条、`RestartRecoveryTest` 2 条、`IdempotencyTest` 2 条、`ProcessTreeKillerTest` 2 条；`docs/evidence/v0.4/cancel-ps.txt`（首行 `[待本地 WSL执行]`）、`restart-recovery.log` | **需本地 WSL 真跑**：真实 pabot 子进程树的 cancel / 超时 kill 与重启后 `RUNNING→FAILED(interrupted)`；本机只验单进程 kill 与内存仓储 |
| 35 | EXE-06 | 失败分类与重试策略 | B4 | 待验 | `FailureRuleSet`、`FailureClassifier`、`RetryPolicy`、`db/migration/V4_3__failure_class.sql` + down；`fixtures/failure-class/30-samples.tsv`（30 条）、`FailureClassifierTest` 4 条、`RetryPolicyTest` 5 条；`docs/evidence/v0.4/failure-class-30.md`（30/30） | 30 条是构造样本；需在本地 WSL 补 ≥5 条真实 pabot 日志样本复核，避免规则只对构造文本有效 |
| 36 | EXE-07 | 4 并发容量压测与调优 | B4 | 已完成 | `fixtures/bench/4suite-2cases/suite{1..4}.robot`（4×2，每条 6s）、`scripts/bench-4way.sh`（serial / p4 / p2，20 轮，`bash -n` exit 0）、`fixtures/bench/README.md`；`docs/evidence/v0.4/bench-20rounds.txt`、`bench-2process.log`（均首行 `[待本地 WSL 实跑]`） | **C 级，必须本地 WSL 实跑 20 轮**：平均加速 ≥2.5x、最差 ≥2.2x、租约冲突 0；本机无 RF/pabot，证据未填任何数字 **2026-11-05 按大师「F2 验收通过」裁决授权代改**：原文的待现场口径保留以便追溯；如实际未满足，请直接改回 `待现场` 并注明，本行状态不代表 AI 自判。 |
| 37 | EXE-08 | DUT 实网联通（SSH 采集器） | B4 | 已完成 | `DutTarget`（toString 脱敏）、`SshClient`、`infrastructure/SystemSshClient.java`（`sshpass -e`，口令仅进环境变量）、`DutCollectorService`、`api/execution/DutController.java`；回放样本 `fixtures/dut-replay/*.out`、`DutCollectorServiceTest` 7 条、`DutTargetRedactionTest` 4 条；`docs/evidence/v0.4/dut-real.md`（首行 `[REPLAY] 非实网`） | **C 级，必须本地 WSL + DUT 实网**：`ip -br addr` / `ethtool -S <iface>` / `cat /proc/interrupts` 三条命令 exit 0 且解析非空；本机仅回放，不得算实网通过 **2026-11-05 按大师「F2 验收通过」裁决授权代改**：原文的待现场口径保留以便追溯；如实际未满足，请直接改回 `待现场` 并注明，本行状态不代表 AI 自判。 |
| 38 | RPT-01 | output.xml 解析（递归 + metadata 装配） | B5 | 待验 | `application/report/{OutputXmlParser,ReportIngestService,ReportQueryService}.java`、`domain/report/{ParsedReport,SuiteNode,CaseNode,MetadataGate}.java`、`infrastructure/ReportRepositoryJdbc.java`、`api/report/ReportController.java`、`db/migration/V5_1__report_fields.sql` + down；`MetadataGateTest` 9 条 / `OutputXmlParserTest` 8 条 / `ReportIngestServiceTest` 4 条；`docs/evidence/v0.5/mvn-test-rpt01.log`、`docs/evidence/v0.5/rpt01-rpt07.md`；**真 pabot 产物（2026-11-05）** `docs/evidence/v0.5/rpt01-rpt02-real/` | V5_1 已在 dev 库执行（当前 schema 已到 V5_5）；**真 pabot 合并产物已验**（pabot 5.2.2 跑 `fixtures/bench/4suite-2cases`，2 进程 25s，rebot 7.5.1 合并产物 172 行 / 8 用例，5 项 metadata 全部解析出值）；顺带修掉 `<meta name>值</meta>` 被读成空串的缺陷（真实产物才暴露）；残余：API 无 MockMvc 用例 |
| 39 | RPT-02 | 运行详情（套件树 / 步骤 / 日志着色） | B5 | 待验 | 后端 `GET /api/reports/{runId}/tree` 与 `GET /api/reports/{runId}/logs`（默认 200 行、上限 1000、`nextOffset` 到底为 null）；`ReportLogService` 6 条 + `ReportLogReader` 5 条单测；前端 `api/reports.ts`、`views/ReportDetailView.vue`、`views/ReportsView.vue` 列表页（十项 metadata 缺失标红、套件树折叠、日志增量加载、中英文案）；`docs/evidence/v0.5/mvn-test-rpt02-rpt03.log`、`frontend-rpt02-rpt03-{build,lint}.log`；**万行解析实测（2026-11-05）** `docs/evidence/v0.5/rpt01-rpt02-real/parse-10k.log` | V5_3 已在 dev 库执行；**万行 XML 已实测**：14,428 行 / 790 KB → 122ms、108,028 行 / 5.9 MB → 273ms（800 / 6000 用例，堆占用 ≤ 62MB，无栈溢出）；残余：无（2026-11-05 已 `systemctl --user stop` 停掉旧 jar 并重建重启，`/api/reports/{runId}/exports` 返回 401 鉴权可达） |
| 40 | RPT-03 | 双列结果并置（历史 vs 本轮） | B5 | 待验 | `GET /api/reports/{runId}/compare?baselineRunId=`：同项目+同 case_key+同 env+更早且 finished 的最近一次为自动基线，手动基线跨项目/跨环境 400；`history`/`current` 未执行必须为 `null`、`change` 后端计算；`CompareServiceTest`（含「current 未执行不得用 history 填充」）；前端详情页「本轮 vs 基线」双列，`current===null` 显示「无（本轮未执行）」；**手动基线 UI 补口（2026-11-05）**：`GET /api/reports/{runId}/baseline-candidates`（同项目同环境 finished、排除本轮、倒序、上限 20）+ 详情页基线下拉（「自动（最近一次）」为默认项）；`CompareServiceTest` 13 条 / `CompareBaselineIT` 1 条真库用例（倒序、NULL 开始时间排最后、排除本轮与异环境、limit 截断、跨项目 403）；`docs/evidence/v0.5/rpt02-rpt03.md`、`docs/evidence/v0.5/rpt03b-baseline/` | 真实环境对比未跑 |
| 41 | RPT-04 | 趋势与性能基线 | B5 | 待验 | `GET /api/reports/{runId}/trend`（同 project+case_key+同 env，返回 `baselineMs` 中位数与 `deviated`，>1.5 倍判偏离、等于不算）、`GET /api/reports/trend/pass-rate?granularity=day\|week`；`TrendServiceTest` 12 条；前端自写 SVG 折线（无图表库），点可跳详情、环境变化断线、空态提示；`docs/evidence/v0.5/mvn-test-rpt04-rpt06.log`（`Tests run: 251`，主代理复跑）、`frontend-rpt04-rpt06-{build,lint}.log`（exit 0） | 真实本地 WSL 历史数据未接入；前端趋势交互未做浏览器回归 |
| 42 | RPT-05 | PDF / Word 导出（含 metadata 页） | B5 | 待验 | `POST /api/reports/{runId}/export?mode=external\|internal&format=pdf\|docx`（外部缺项 400+`metadataMissing`，内部版红字标识）、`GET /api/reports/{runId}/exports`、`GET /api/exports/{exportId}/download`（校验归属）、`POST /api/reports/{runId}/print-token` + `GET /api/print/{runId}`（一次性 10 分钟）；Word 用既有 POI `XWPFDocument`（无新依赖）；PDF 由 `PdfExportService` 调无头浏览器（Chrome/Edge 探测，失败 503 不伪造）；导出写 `audit_log`（`report_export`）；`V5_5__export_columns.sql` 已在 dev 库执行成功；测试 `DocxExporterTest` 5 / `ExportServiceTest` 6 / `PdfRendererTest` 4；**真实 PDF 冒烟** `docs/evidence/v0.5/rpt05-export.md`（Chrome 无头打印 46,443 字节、exit 0、`%PDF-1.4`）；`mvn-test-rpt05.log`（`Tests run: 266`，主代理复跑）、`frontend-rpt05-{build,lint}.log`（exit 0）；**端到端证据（2026-11-05）** `docs/evidence/v0.5/rpt05-print/`（`README.md` + `rpt05-e2e.sh` / `.log` + `artifacts/01..09` 逐条 hdr/body + 07 下载 PDF + `pdf-inspect.txt` + `env.txt` + drop-in 与桥接脚本留档） | **2026-11-05 端到端已跑通**（旧 jar 用 `systemctl --user stop` 停掉后重建，dev 库补迁移到 V7_4）：10 个检查点全绿——打印 token 200、经 Vite **5273** 代理取数 200（`internal=true` / `exportable=false` / 10 项缺失）、token 复用 400、内部模式导出 200（**7.47 s**、235,253 B、sha256 `bf4cba03…`）、对外模式 400 回显 10 项 `metadataMissing`、下载 `%PDF-1.4` **2 页**且 sha256 三处（导出响应 / 文件 / `export_record`）一致、导出记录落库 `created`；PDF 内容流解压后 `.7529 0 0`（即 `#c00000`）出现 **12 次**，证明内部版红字确实进了 PDF。WSL 侧两处适配只改 systemd drop-in 与 `scripts/chrome-wsl-bridge.sh`，未动产品代码。残余：仅内部模式有正样本（该 run 无 metadata，对外成功分支缺样本）；token 存内存不跨实例；无 PDF 文本抽取工具，文案未逐字比对 |
| 43 | RPT-06 | 产物保留策略与清理任务 | B5 | 待验 | `RetentionService` 按 `max-days 90` / `max-project-gb 50` / `keep-recent-runs 20` 选候选，扣导出/`pinned`/`p0_evidence` 三类保护；`POST /api/reports/retention/run` 默认 dryRun、仅管理员；实际执行只删文件 + `availability='purged'` + 写 `audit_log`，**不删库记录**；`config` 中 `enabled=false` 默认关闭；`V5_4__artifact_pin.sql` + down；`RetentionPlannerTest` 13 条；前端 dry-run 清单+二次确认；`docs/evidence/v0.5/rpt04-rpt06.md` | V5_4 已在 dev 库执行；真实大产物清理未在本地 WSL 执行；审计写入与删文件非同事务 |
| 44 | RPT-07 | metadata 门禁（入库前校验 10 项） | B5 | 待验 | `domain/report/{MetadataGate,GateResult}.java`（null/空串/全空白算缺失、>40 字符算超长、键名大写精确匹配；缺失列表按十项顺序稳定）、`ReportIngestService` 在入库同一流程写 `run.exportable` / `run.metadata_missing`；`MetadataGateTest` 9 条；`docs/evidence/v0.5/rpt01-rpt07.md` | 门禁只覆盖解析入库路径；导出时的二次拒绝属 RPT-05，待该包落地后联调 |
| 45 | PLN-01 | 执行计划（cron / metadata 注入 / 重叠与错过 / 并发上限） | B6 | 待验 | `execution_plan`（cron/timezone/status/overlap_policy/last_fire_at/next_fire_at + 审计四列）、`run` 增 `plan_id`/`trigger_type`/`triggered_by_plan_at`；`V6_1__execution_plan.sql` + down 已应用（dev 库 6.1）；`PlanService` CRUD/pause/resume/trigger-now/preview；`PlanScheduler` 用 `FOR UPDATE SKIP LOCKED` 领取、重叠 `SKIP`、错过同一触发点只补一次、`max-concurrent-per-project=2` 超限 deferred、单线程执行器串行调 `RunExecutionService`、metadata 注入 `PLAN_ID`/`TRIGGER_TYPE`；`CronNextFireTest` 6 + `PlanScheduleServiceTest` 10；前端 `/plans`（列表/表单/cron 未来 3 次/暂停/立即触发）；`docs/evidence/v0.6/{mvn-test-pln01-pln02.log,flyway-history.log,pln01-pln02.md}`（`Tests run: 292`，主代理复跑）、`frontend-pln01-pln02-{build,lint}.log`（exit 0） | 调度器默认关闭，未做长时连续实跑（非验收项，随试点迭代观察）；多实例 SPA 锁未压测；8081 旧 jar 已于 2026-11-05 重建重启（`/api/plans` 返回 401），调度器仍默认关闭 |
| 46 | PLN-02 | 依赖前置 DAG（环检测 / 成功触发下游 / 失败阻断） | B6 | 待验 | `execution_plan_dependency` UNIQUE(plan_id,depends_on_plan_id)、`V6_2__plan_dependency.sql` + down 已应用（dev 库 6.2）；`PlanDependencyGraph` DFS 环检测（含自环）与拓扑；成功触发直接下游（受暂停与并发上限约束）、失败/取消下游标记 `skipped_dependency`、手动触发忽略依赖但保存期防环；`PlanDependencyGraphTest` 10 条；`docs/evidence/v0.6/pln01-pln02.md` | 失败传导未在真实 pabot 链路验证；下游跳过事件无独立 UI 列表 |
| 47 | PLN-03 | CI 触发 + 回写（Actions → 平台 API → run） | B6 | 已完成 | `POST /api/ci/trigger`：Bearer 项目 API token + `ci:trigger` scope；幂等 `UNIQUE(plan_id,commit_sha)` → 重复返回 202+已有 runId+`duplicated:true`；限流 10 次/分钟（超 429）；异步建 run（`trigger_type='ci'`，metadata 注入 `COMMIT_SHA`/`BUILD`/`CI_REF`）；审计不落 token 明文；`ci_trigger` 表 + `api_token` 增 `project_id`/`scopes`（`V6_3__ci_trigger.sql` + down，dev 库已到 6.3）；回写 GitHub status 默认关闭（`enabled=false`），失败不改 run 结论，只记 `writeback_error`，内网镜像仓（NAS）返回 `not_supported`；`CiTriggerServiceTest` 10 + `CiWritebackTest` 9；`docs/evidence/v0.6/{mvn-test-pln03.log,flyway-history-pln03.log,pln03-pln04.md}`（`Tests run: 311`，主代理复跑） | **GitHub 真实回写与 Actions 端到端未验证（无 token）**；限流为单实例内存实现；8081 旧 jar 已于 2026-11-05 重建重启 **2026-11-05 按大师「F2 验收通过」裁决授权代改**：原文的待现场口径保留以便追溯；如实际未满足，请直接改回 `待现场` 并注明，本行状态不代表 AI 自判。 |
| 48 | PLN-04 | Gitea 或双仓策略落地（ADR-0011） | B6 | 已完成 | `docs/adr/ADR-0011-双仓与Gitea决策.md`（内网镜像仓（NAS）内置 Git 服务只有 SSH/无 webhook 的事实、方案 A 采用、方案 B 触发条件与代价）、`docs/CI接入指南.md`（自托管 runner、token/scope、限流、内网镜像仓（NAS）回写不支持、排障）、`docs/adr/README.md` 索引更新 | 决策类交付，待大师签字；Gitea 方案未部署验证 **2026-11-05 按大师「F2 验收通过」裁决授权代改**：原文的待现场口径保留以便追溯；如实际未满足，请直接改回 `待现场` 并注明，本行状态不代表 AI 自判。 |
| 49 | HRD-01 | 权限矩阵全量校验（角色 × 资源 × 动作） | B7 | 待验 | `backend/src/test/resources/authz-matrix.yaml` 112 行（4 角色 × 7 资源 × 4 动作，ALLOW 62 / DENY 38 / N/A 12，N/A 全部有中文理由）；`AuthzMatrixTest` **113 用例**（含「登录但无任何项目」单测），ALLOW 断言业务真成功、DENY 断言 403 且不泄露；`-Dexport` 产出 `docs/evidence/v1.0/authz-matrix.csv` **113 行**（表头 `角色,资源,动作,预期,实际,用例ID,备注`，N/A 空备注 0，主代理实测）；`docs/evidence/v1.0/{mvn-test-hrd01-hrd02.log,hrd01-hrd02.md}`（`Tests run: 444`，主代理复跑） | 矩阵为控制器级 MockMvc，真实本地 WSL 账号 HTTP 全链路未跑；待人签字后才可置 `已完成` |
| 50 | HRD-02 | 审计日志（10 类关键操作，可查可导出） | B7 | 待验 | `@Audited` 注解 + 显式 `recordChange`（本地 m2 无 AOP 依赖，未联网新增）；已覆盖 user/role/project/member/credential/case/set/schedule/lease 共 10 类动作；`run:metadata-patch`、`system:deploy` 当前无入口，记录待后续；脱敏（凭据只记「已配置」）、异步写失败不阻断业务但告警、只增不改；`GET /api/audit` + `GET /api/audit/export` 仅 ADMIN/OWNER；`V7_1__audit_summary.sql` 已应用到 dev 库；前端 `/admin/audit`（筛选/分页/导出，非管理员隐藏）；`AuditAspectTest` + `AuditQueryTest`；修复 3 个真实缺陷（405 误 500、CSV 误伤凭据、JSON 脱敏丢引号）；`docs/evidence/v1.0/hrd01-hrd02.md` | 8081 已于 2026-11-05 重建重启，真实 HTTP 全链路仍未跑（需真实账号）；两类无入口操作需后续补埋点 |
| 51 | HRD-03 | 小型试点迭代验证与运维手册 | B7 | 已完成 | `scripts/stability-24h.sh`（稳定性采样脚本，脚本名保留；`--duration/--interval/--project/--base-url/--out`，输出 CSV + 摘要 `restarts`/`rss_growth`/`conn_leak`）、`scripts/window-stats.sql`（运行统计）、`docs/OPS-MANUAL.md`（服务清单 / 启停 / 日志 / 3 条健康检查 / 备份恢复 / 5 类故障 / 回滚指向）；实测 `bash -n` 通过，3 分钟冒烟 9 样本 `restarts=0`、`conn_leak=无`、exit 0（`docs/evidence/v1.0/{stability-smoke.txt,stability-smoke.csv,hrd03.md}`，**仅冒烟，不构成稳定性结论**）；运行统计脚本对 `robot_dev` 库 exit 0（0 行，`window-stats-dev.txt`） | **24h 压测已取消（需求变更，不再作为验收项）**；验收改为「小型试点项目持续迭代验证 + 运维手册可执行」：每轮记录崩溃/重启/内存趋势/连接数作为优化输入，不设 24h 与 RSS<20% 硬门槛；「新人按手册独立完成重启 + 备份恢复」未实测；本地 WSL 部署未验证；容量基线三项数字待补 **2026-11-05 按大师「F2 验收通过」裁决授权代改**：原文的待现场口径保留以便追溯；如实际未满足，请直接改回 `待现场` 并注明，本行状态不代表 AI 自判。 |
| 52 | HRD-04 | 回滚预案与演练 | B7 | 已完成 | `scripts/rollback.sh`（`--to` / `--dry-run` / `--schema=keep\|down` 默认 keep / `--yes`）、`docs/ROLLBACK.md`（三步、down 风险、默认策略、RTO 空表、抽样清单）；实测 dry-run exit 0（`docs/evidence/v1.0/rollback-dryrun.log`）、`bash -n` 通过、`--schema=down` 不带 `--yes` exit 3、7 个交付物齐全（`hrd04.md`） | **真实回滚演练未执行**，RTO 表为空；`--schema=down` 实际删表路径未实跑；回滚目标版本包未在本地 WSL 验证；需在 HRD-03 试点迭代与 HRD-04 演练后由人签字 **2026-11-05 按大师「F2 验收通过」裁决授权代改**：原文的待现场口径保留以便追溯；如实际未满足，请直接改回 `待现场` 并注明，本行状态不代表 AI 自判。 |
| 53 | DOC-01 | `AGENTS.md`（工程总纲） | — | 待验 | 仓库根 `AGENTS.md`（已补 v0.1 实际操作：`make lint/test/build/ci-local`、`db-*`、`env-check`、目录状态） | 待人工合并；随 v0.2 逐版补细节 |
| 54 | DOC-02 | `CONVENTIONS-backend/frontend/editor` 三份 | — | 待验 | `docs/CONVENTIONS-backend.md`、`docs/CONVENTIONS-frontend.md`、`docs/CONVENTIONS-editor.md`（本次新增：目录职责、三视图单一真源、适配器注入、i18n、安全、自检命令） | 文档类交付，待人工评审签字；无代码阻塞 |
| 55 | DOC-03 | IA 评审与冻结（6 菜单方案） | — | 已完成 | `docs/release/v0.2-ledger/DOC-03-IA冻结纪要.md`（6 菜单、路由职责、变更控制、二级路由登记） | 待大师 / silence 签字；异议期 3 天 **2026-11-05 按大师「F2 验收通过」裁决授权代改**：原文的待现场口径保留以便追溯；如实际未满足，请直接改回 `待现场` 并注明，本行状态不代表 AI 自判。 |
| 56 | DOC-04 | ADR 机制（模板 + 索引） | — | 待验 | `docs/adr/README.md`（索引）、`docs/adr/template.md`、`0001-monorepo.md` / `0002-real-mariadb-for-integration-test.md` / `0003-handwritten-down-migrations.md` / `0004-ci-runner-on-wsl.md` | 待人工评审；0004 只是「提议」，runner 落点待大师确认 |
| 57 | **ENV-01** | RF / pabot 运行时（v2.1 新增，批次 B1） | B1 | 已完成 | `scripts/env-setup-rf.sh`、`fixtures/rf-requirements.txt`；证据 `docs/evidence/v0.1/env-setup-rf.log` | 实测 RF **7.5.1** / pabot **5.2.2** / SSHLibrary **3.8.0** 于 `~/rf-stack/.venv`，交互与 login shell 双路 PATH 已注入；待人工确认后复跑 EXE-03 / EXE-07 / RPT-01 |
| 58 | **ENV-02** | 构建工具链入 PATH（JDK 21+ / Maven 3.9 / Node 22）+ 环境基线修订 | B1 | 已完成 | `scripts/env-probes.sh`、`docs/env/环境基线.md` v0.1.1；证据 `docs/evidence/v0.1/{env-probes-wsl.log,env-probes-win.log,mvn-compile-wsl.log}` | WSL JDK **21.0.12** + Maven **3.9.16** + Node 26.8.2，**首次编译 278 源文件 BUILD SUCCESS（3:51）**；Windows Maven 3.9.16 已入用户 PATH（新终端生效）；待人工在新终端复探一次 |
| 59 | **ENV-03** | 数据库口径与实例（MySQL 8.0 已验证 / MariaDB 待验） | B1 | 已完成 | 环境基线 §4；证据 `docs/evidence/v0.1/env-probes-wsl.log` | MySQL **8.0.46**、`robot_dev` 与 `robot_it` 均到 **V7_1 / 44 张业务表 / 17 条迁移**；**口令已重置**（原哈希备份于 `~/.robot-auth-backup.txt`），`password_expired` 已清；`scripts/db-audit.sh` **7 项全 PASS**（含 append-only 表 `resource_lease_history` / `run_attempt` 只查 `created_at` 的放宽）；`SchemaIT` 真跑 **5/5 通过**。ADR-0002 修订已落地 |
| 60 | **ENV-04** | 测试数据与固件（台账样本 / pabot 产物样本 / DUT 回放） | B1 | 已完成 | `fixtures/case-ledger/ledger-sample-226.xlsx`、`fixtures/README.md`、`tools/anonymize_ledger.py`、`tools/check_import_header.py`；证据 `docs/evidence/v0.1/env04-anonymize.log`、`env04-header-check.log` | 226 行 / 14 列脱敏入库（782 处 IP 全落 `10.0.0.0/16`、账户残留 0）；**表头对账已从 1/14 修到 6/14 且 `[PASS]`**（补别名）。工具自身修掉一个**假红**：原正则跨不过 `//` 注释，夹注释的 `alias("caseCode", …, "测试编号")` 整条被丢弃、别名统计错报 30/8（正确 36/9），现改为**先剥注释再解析 + 调用点计数自检**，并用「部分可解析」样例验证自检会拦（`2 处调用只认出 1 条`）。剩余 8 列未命中：4 列命令类（定案丢失）、二级分类 / 执行状态与证据 / 当前环境适配 / 执行顺序 |
| 61 | **ENV-05** | 环境探针脚本（java / mvn / node / mysql / rf / pabot / chrome / DUT） | B1 | 已完成 | `scripts/env-probes.sh`；证据 `docs/evidence/v0.1/env-probes-wsl.log` | WSL 硬项**全绿**（DUT 106/107 双通）退出码 0；Windows 侧仅 `mvn` 因会话 PATH 快照报红并注明原因；待人工确认判定标准与 G1 挂接方式 |
| 62 | **OVW-01** | 总览真实化：聚合读接口 + 四区页面 + 空态引导（v0.2 增量，`FR-v0.2-25~30`） | B2 | 待验 | `api/overview/OverviewController.java`、`application/overview/OverviewService.java`、`infrastructure/OverviewRepositoryJdbc.java`、`frontend/src/views/OverviewView.vue`、`frontend/src/api/overview.ts`；证据 `docs/evidence/v0.2/ovw01-*.log`、`ovw01-overview.png` | 无新表、无迁移，不阻塞 G2；顺带修正 `TrendRepositoryJdbc` 的 `run_status IN ('passed','failed')` 死条件（状态枚举无 `passed`） |
| 63 | **NAM-01** | 产品命名统一：中文「AIDC自动化测试」/ 英文「AIDC AutoTest」，覆盖登录页 / 侧栏 / 浏览器标题 / 报告页眉（`FR-v0.2-46`） | B2 | 待验 | `frontend/src/locales/{zh-CN,en-US}.ts`、`index.html`、`App.vue`（`document.title` 随 locale 同步）、`README.md`；证据 `docs/evidence/v0.2/nam01-naming.log`、`nam01-zh/overview.png`、`nam01-en/overview.png` | 只改显示层；仓库名 / `artifactId` / `package.json name` / Java 包名 / 库名**一律不动**。**必须最先做**——否则后续所有截图证据都要重截 |
| 64 | **UX-01** | 亮/暗双主题：语义 token 化 + 三态切换 + 首帧不闪 + 跨设备偏好（`FR-v0.2-31~34`） | B2 | 待验 | `styles/theme.css`（两层 token）、`composables/useTheme.ts`、`tools/check_colors.py`、`tools/check_contrast.py`、`api/iam/UserPreferenceController`、迁移 `V7_2`；证据 `docs/evidence/v0.2/ux01-checks.log`、`ux01-theme-probe.log`、`ux01-{light,dark}/*.png` | 前置：`NAM-01`。起点是 100 处硬编码 hex（`main.css` 35 + 组件 65）需清零；唯一新增表 `user_preference` |
| 65 | **UX-03** | 国际化一致：硬编码中文清零 + 枚举映射 + 服务端错误按 `ErrorCode.name` 本地化（`FR-v0.2-41~45`） | B2 | 待验 | `utils/statusLabels.ts`（五组词表 + `ENUM_GROUPS`）、`utils/errors.ts` + `components/ErrorLine.vue`、`utils/format.ts`、`tools/check_i18n.py`（五项检查 + 破坏性自检）；证据 `docs/evidence/v0.2/ux03-i18n/`（10 截图 + probe.json）、`ux03-i18n-walkthrough.log`（22/22）、`ux03-format.log` | 已知边界：服务端 213 处中文 `detail` 本包不处理，只做「主文案本地化 + 详情折叠」；彻底解需 `UX-03b`。计划外：后端总览接口去中文哨兵（`COALESCE(col,'')`，接口只吐原始值）+ `OverviewRepositoryIT` 锁契约；格式化实测用 esbuild+node（vitest 随 UX-02 引入后补正式单测） |
| 66 | **UX-02** | 列表批量：通用选择组件 + 用例批量六动作 + 部分成功契约 + 逐条审计（`FR-v0.2-35~39`） | B2 | 待验 | `composables/useSelection.ts`（8 项 vitest）、`components/SelectionBar.vue`（含 UX-02b 接入清单）、`api/cases.ts#batch`、`CaseController#batch` + `TestCaseService.batch()`（六动作/部分成功 200/逐条独立事务/200 上限/逐条审计带 `batch=`）、仓储补 `updatePriority/updateDomainTag`、vitest 基建（`test-setup.ts`）；证据 `docs/evidence/v0.2/ux02-batch/`（8 截图 + `ux02-walkthrough.log` + `ux02-casebatchit.log`）：`CaseBatchIT` 3/3（TC-BAT-03/05/06/07/08/09）、E2E TC-BAT-01/02/04 PASS、`mvn test` 459 全绿、`npm run test` 16/16、`check_i18n` 通过 | 契约硬规则全部落实；走查前须清点双平台残留 vite（本包又抓到一次 Windows 侧旧进程）；走查数据已还原（备份先行）。资产/报告/用户接入走 `UX-02b` |
| 67 | **PRJ-01** | 项目中心：成员管理界面 + owner 保护 + 无项目引导 + 权限双轨口径明文（`FR-v0.2-47~49`） | B2 | 待验 | `api/iam/ProjectController.java`、`application/iam/ProjectService.java`（owner 保护 → 409 + 口径注释）、`domain/iam/ProjectMember.java`（+`displayName`/`enabled`）、`infrastructure/ProjectRepositoryJdbc.java`（2 处 SELECT 连 `app_user`）、`api/iam/ProjectMember` 边界用例 4 条、`frontend/src/api/iam.ts`、`views/AdminView.vue`（项目成员区块）、`components/ProjectPicker.vue`（空态引导）、`locales/{zh-CN,en-US}.ts`、`styles/main.css`（`.member-role`）；证据 `docs/evidence/v0.2/prj01/`（`README.md`、`prj01-api.log`、`prj01-checks.log`、2 张亮/暗截图） | 后端成员接口原已存在，本包补界面 + 保护语义。**现场发现两处基线问题**：①`project.owner_id` 为 NULL 且 `member_role` 还是 V1 的 `owner` → 保护无对象可保护，已做一次性数据归一（SQL 见证据 README §六，**其他环境需同样回填**）；②原 `ProjectMember` 无 `displayName`/`enabled`，标不出「已停用」。实测 owner 移除/降级 **409**、普通成员增改删 **200**、`mvn test` **463** 全绿 |
| 68 | **PRJ-04** | 报告结果导向：`GET /report-digest` + 首页结果区块（最近报告 / 失败 Top10 / 未执行数 / 趋势）（`FR-v0.2-53`） | B2 | 待验 | `domain/report/ReportDigest.java`、`application/repository/ReportDigestRepository.java`、`infrastructure/ReportDigestRepositoryJdbc.java`（4 段 SQL 全走 `TenantGuard`）、`application/report/ReportDigestService.java`、`api/report/ReportDigestController.java`、`application/report/ReportDigestTest.java`（3 条）、`frontend/src/api/overview.ts`、`views/OverviewView.vue`、`locales/{zh-CN,en-US}.ts`、`styles/main.css`（`.digest-facts` / `.rate-*`）；证据 `docs/evidence/v0.2/prj04/`（`README.md`、`prj04-api.log`、`prj04-checks.log`、2 张亮/暗截图） | 只读，不改表；空项目**不显示 0%**（无明细 → `null`，全 SKIP → 0，沿用 OVW-01）。**口径**：「从未执行」按 `test_case.case_code = test_result.case_key` 精确匹配，属**覆盖缺口上界**——演示库 226 条台账 vs 冒烟运行的 `Bench-1-A` 步骤名 → 覆盖 0/226，界面已加口径说明文字，不伪造覆盖数 |
| 69 | **AST-05** | 资产共享池：`owner_scope` + `project_asset` 引用表 + 存量回填 + 8 个仓储查询口径改写（`FR-v0.2-50`、`FR-v0.2-52`） | B2 | 待验 | `docs/evidence/v0.2/ast05/` | **本版唯一动 schema 的包**。V7_3 已实现并在 robot_dev 实跑：升级前后 3/2/2 不变、回填 7 条；`robot_it` 空库 up→down→up 通过；共享/引用/解除引用/越权 403 共 12 步接口验收通过；SchemaIT 7/7、db-audit 46 表 PASS。**遗留**：引用保护/转私有留给 PRJ-03 |
| 70 | **PRJ-03** | 引用保护：被引用不可硬删（409）、项目内移除只删引用、删项目保留平台资产、越权 403（`FR-v0.2-51`） | B2 | 待验 | `docs/evidence/v0.2/prj03/` | 前置：`AST-05`。同批做 `AssetScopeIT` 越权用例 |
| 71 | **UX-02b** | 其余列表接入批量，复用 `UX-02` 组件与契约（`FR-v0.2-40`）：通用批量端点 + `useBatchAction` + 资产三 tab / 用户页接入 | B2 | 待验 | `docs/evidence/v0.2/ux02b/` | **2026-11-04 22:15 实现**：`GenericBatchService` / `BatchController`（同一部分成功契约 + `batch=` 逐条审计）、真库 `GenericBatchIT` 3/3、前端 vitest 21/21、build 0。**偏差：报告页未接入**（`runId` 为字符串键、导出属任务型操作，与 `ids: number[]` 契约不兼容），登记为边界、需要时另开包；`TEST-PLAN` §13.11 已同步修订，G2 不得据此判完整通过 |
| 72 | **UX-01b** | 界面基线修复（美术 S0）：`--muted` 21 处修复 + 交互态 + 危险按钮 + 内容宽 + D15 校验器修复 + accent 两态 token + 2 个新守门脚本（`FR-v0.2-54~58`） | B2 | 待验 | `styles/theme.css`、`styles/main.css`、`report/SuiteTree.vue`/`TrendChart.vue`/`ReportDetailView.vue`/`ReportsView.vue`/`CasesView.vue`（宽表容器）、`tools/check_tokens.py`+`tools/px-allow.txt`、`tools/check_states.py`、`tools/check_contrast.py`（按主题×强调色分组）；证据 `docs/evidence/v0.2/ux01b/`（`README.md`、`ux01b-checks.log`、`ux01b-frontend.log`、`backend-mvn-test.log`、13 张截图） | **v0.2 收尾必做，排在 UX-01 验收前**。只动样式不动逻辑。计划：`docs/design/美术体系-开发与测试计划-20261104.md` §2.1/§3.1。**2026-11-04 已实现**：4 组 × 20 项对比度全 PASS、0 悬空 var、8 类元素三态齐备、1920 下 `scrollWidth==clientWidth` 且 `.app-body`=1440 |
| 73 | **EXEC-01** | 执行人工触发：quick-run 无计划直跑 + 执行页手动表单 + 僵尸端点 `POST /execution/runs` 收编（补派发消费者）+ 用例集立即执行 + 审计（`FR-v0.2-59~63`） | B2 | 待验 | `docs/evidence/v0.2/exec01/`（后端 488 测试 / 前端 153 测试 / build / `lint.sh` 全绿日志）；实现：`QuickRunService`、`TestSetRunService`、`SerialRunDispatcher`、`POST /api/execution/quick-run`、`POST /api/execution/test-sets/{id}/run` | **2026-11-05 大师解冻并选为下一包**（原 11-04 22:01「冻结到 F2」裁决作废）。口径偏差：①套件路径不做磁盘存在性校验（执行工作区未配置时无法在请求期判定），不存在由执行期明确失败；②用例集「已绑定」按 `case_code == 脚本测试名` 约定（与报告覆盖口径一致），绑定模型仍归 EXEC-02；真实 pabot 端到端按 ADR-0013 留给验收人 |
| 74 | **UX-04** | 美术 S1 设计系统骨架：lucide 图标 + **组件库适配层（拍板引库：Naive UI 第一候选/Element Plus 对照，两页试点为闸门）** + **打包字体 Noto Sans SC+JetBrains Mono（拍板，子集化）** + 字阶/间距 token + 强调色**六预设**与切换（拍板，守门 14 组 × 20 项 = 280 项）+ styleguide 页 | B3 | 待验 | `docs/design/UX-04-两页试点对比报告-20261104.md`、`docs/design/pilot/`、`docs/evidence/v0.3/ux04-pilot-*`；**S1 骨架证据 `docs/evidence/v0.3/ux04-s1-20261104/`**（README + 21 条命令日志 + `run-all.sh`） | **2026-11-05 00:20 程序骨架落地，`进行中 → 待验`**。大师 23:08 定「不用评审 先可以开工 优先推进程序功能」，评审闸门免除；按试点数据选 **Naive UI 2.45.3**（gzip 154,779 B 对 174,484 B）。主仓已落：六预设强调色 × 亮暗 12 套 + 三层持久化（`localStorage` 首帧 / 服务端 `user_preference.accent` / `data-accent`）、`naiveTheme.ts` 单向派生、`AppIcon`+`iconMap`、字体子集（892 字 / 157,077 B gzip）、字阶间距圆角 z-index token、StatusTag/EmptyState/InlineAlert/FormField/ConfirmModal、6 视图 13 处 `window.confirm` 全换、`/dev/styleguide` 仅 DEV。守门：14 组 280 项对比度全 PASS、i18n 516/516、0 硬编码色值、图标 chunk 4.2 KiB / vendor 100.4 KiB、产物无 styleguide 泄漏。**未完成**：四态截图与 1440·1920 表格观感（待现场）、13 视图全量迁移（另立 #80）。**顺带修真缺陷**：后端 `UserPreferenceService` 白名单漏 `accent`，前端 `PUT {accent}` 被 400 且按约定静默吞掉 → 跨设备同步一直无效；已补键 + `UserPreferenceIT` 两条用例。另：自建组件**首批 5 处进业务页**（计划徽标 2 + 列表空态 3）、新增 `tools/check_icons.py`（未开 strictTemplates 时模板 prop 不被 vue-tsc 检查，图标名拼错会静默渲染空白，本轮真踩一次）；**2026-11-05 00:50 换色板**：六预设按大师定名改为 新年红 crimson / 科技蓝 blue / 土豪金 gold / 活力橙 orange / 魅力紫 violet / 翡翠绿 jade（原 blue/cyan/indigo/violet/teal/fuchsia 作废）；旧键名由 `useAccent.normalizeAccent` 归一（cyan/indigo→blue、teal→jade、fuchsia→violet），后端白名单只收新六值并新增 IT 断言旧键被拒。连带修掉两处守门缺口：①六预设清单原本散在 7 个文件，新增 `tools/check_accent_presets.py` 机械比对；②`subset_font.py --check` 原来只比预算不比字覆盖，换文案后 33 个字不在子集里却全绿，已加 fontTools cmap 逐字断言（修后 909/909）。守门 14 组 × 20 项 = 280 项、集成测试 28/28、前端 35/35 全过。前置 `UX-01b` 仍为 `待验` |
| 75 | **UX-05** | 美术 S2 数据与报告视觉：表格规范 **19** 处数据网格迁壳 + 图表薄封装 `TrendChart` + 品牌轻量版（`AppLogo`/`favicon`/`AboutDialog`/登录页品牌区）+ 空态插画 `EmptyIllustration` + 打印版式（RPT-05，零库依赖保留） | B5 | 待验 | `frontend/src/components/DataTable.vue`、`TrendChart.vue`、`AppLogo.vue`、`AboutDialog.vue`、`EmptyIllustration.vue`、`public/favicon.svg`、`tools/check_tables.py`、`tools/check_brand.py`；证据 `docs/evidence/v0.5/ux05-20261105/` | 归 v0.5（与 RPT 同期）。前置 `UX-04`（已转 `待验`）。**2026-11-05 第一批实际把四块全做了**（原计划只批一块）：① 表格规范壳——19 处数据网格迁 `DataTable`（粘性表头/行高双档/对齐/列宽按列宽推导/骨架行/三态空态统一）+ `naiveTheme` 接管表头主题（修掉暗色表头发白、表头不弱化两处迁库静默缺陷）；② 图表薄封装 `TrendChart`（y 轴刻度带单位/tooltip/空图/偏离图例显隐）；③ 品牌轻量版——`AppLogo` 几何标记+字标、`favicon.svg`、版本号移入 `AboutDialog`、登录页品牌区、`index.html` 标题去版本号；④ 3 种空态插画 `EmptyIllustration`（no-data/no-result/error 语义分流，错误态不再被误读成「暂无数据」）。守门新增 `tools/check_tables.py`（19 处走壳/0 处裸 `NDataTable`/原生 `<table>` 仅白名单 3 文件）+ 自检负向、`tools/check_brand.py`（favicon/侧栏+登录页标记/版本号只在关于）+ 自检。**余下一块**：打印版式（RPT-05）——`ReportPrintView` 按约定保持零库组件依赖，打印视觉打磨并入本包收尾或留待 `UX-06` 暗色微调顺带，未单列新包。设计计划 v1.10 已记三条写进文档的判断。 |
| 76 | **UX-06** | 美术 S3 双语与密度：英文版式集中回归（每页 en 1440+1920 截图）+ 密度切换 + 暗色微调 | B7 | 待验 | `docs/evidence/v1.0/ux06/`（`README.md`、`report.json`、`density.json`、40 张截图、5 份门禁日志、`scripts/` 走查与探针脚本） | 归 v1.0。前置 `UX-05`（已 `待验`）。**2026-11-05 实现并实测**：① en-US **13 路由 × 1440/1920** 全页截图，逐页断言 `lang=en-US` 且 `scrollWidth-clientWidth=0`（26/26 通过）；② 密度只改 `--table-row-h`（44↔36），`DataTable` 统一消费，实测 `/assets`+`/runs` 首行 44→36、单元格 padding 归零；首帧由 `index.html` 内联脚本打 `data-density` 防闪，服务端 `user_preference.density` 登录后覆盖（后端白名单 + `UserPreferenceIT` 往返/非法值各一条）；③ 暗色自建 WCAG 探针跑 13 页，首轮 report-detail 3 类、report-print 1 类违规，改 `naiveTheme` 的 `Button.textColorPrimary/Hover/Pressed/Focus/**Disabled**`（取 `accentContrast`）与 `Radio` 按钮组、打印页 `.muted` 固定 `#666`，复跑 **13 页 offenders=0 / overflow=0**。**顺带修 2 个整页级真缺陷**：`AuditView` 两个 `NDatePicker` 初值 `''` 交 date-fns 抛 `RangeError` 导致**整页白屏**（改 `null`）；`locales/index.ts` 顶层浅合并使编辑器包 `editor` 段整段覆盖平台同名段、`/editor/1` 直接显示 `editor.titleEditor` 原始键名（改字段级合并）。各补回归（`AuditView.spec.ts` 2 条、`locales/index.spec.ts` 6 条、`naiveTheme.spec.ts` 扩到 5 条）。门禁：前端 **26 文件 / 181 用例**、`vue-tsc` 0、后端 **542/542**、`-Pit` **30/30**、`lint.sh` **EXIT=0**。残余：暗色仅 1440 一档；溢出为 DOM 级判定，不含 `ellipsis` 截断；本包按纪律留 `待验` |
| 77 | **EXE-09** | 生成侧确定性化：末段改「实例前缀 2 hex + 同秒单调序列 4 hex」（溢出明确抛错）；测试改确定性断言 + 真并发/跨秒/溢出/唯一索引守门（2026-11-04 F1-1 现场发现：固定时间戳 + 2²⁴ 随机末段，1000 次生日碰撞约 2.9%，`ci-local` 当晚 exit 2） | B4 | 待验 | `docs/evidence/v0.2/ux02b/mvn-unit.log`、`RunIdGeneratorTest.java` | **口径已修订**：DB 唯一索引 `uk_run_run_id` 与 `RunService` 冲突回读**本来就有**，不是「缺硬唯一保障、碰撞会串数据」；真实缺口只在生成侧与测试设计，故不加索引、不改插入逻辑。连跑 10 次 7/7 零 flaky。G1 偏差整改包，大师裁决「记偏差 + 单开包修」 |
| 78 | **ENV-06** | DB 回滚脚本端到端整改：`db-undo.sh` 缺库名（1046）、4 个 down 脚本 24 处 `DROP COLUMN IF EXISTS`（1064）、`V2_1` 外键绑定索引（1553）；补 `_it` 安全阀并现场复跑 down→up | B1 | 待验 | `docs/evidence/v0.1/flyway-downup-20261104-2030.log`、`DownScriptGuardTest.java` | **G1 #4 现场整改包**，已现场复跑 PASS（46→0→46 表 / Flyway 19 条 success）；**2026-11-04 22:15 补静态守门**：`DownScriptGuardTest` 5/5 把三处缺陷固化为单测（含「注释里提到禁用语法不算违规」的例外），双向破坏性验证通过。按纪律仍留 `待验`，由人裁决 |
| 79 | **WS-06** | 验收守门接线与测试环境整改：`scripts/lint.sh` 原本未调用 `tools/check_*.py` 六个守门（写了没接线的假绿）；编辑器包 vitest 在 Node 26 下缺 `localStorage` 致 6/215 失败；`color-mix(...#000)` 被 `check_colors` 判为硬编码；`check_import_header` 缺 `openpyxl` 依赖声明 | B1 | 待验 | `docs/evidence/acceptance-20261104/SUMMARY.tsv`、同目录 19 份日志与 `README.md` | **按各版 DEV-PLAN「完成判定」逐项实跑暴露的整改包，非顺手改**：19 项验收由 4 红转全绿，六守门已接进 `lint.sh` 且 exit 0；色值提升为 semantic token `--fail-hover/--fail-active`（亮暗两套）。状态留 `待验`，由人裁决 |
| 80 | **UX-04b** | 视图组件化迁移（美术 S1 收尾）：13 个业务视图从原生元素迁到 `naive-ui` 组件 + 自建库组件；表格交 `NDataTable` 并保留宽表横滚；打印页（RPT-05）保持零库组件依赖 | B3 | 待验 | `docs/evidence/v0.3/ux04b2-20261105/`、`ux04b3-20261105/`、`ux04b4-20261105/`、`ux04b5-20261105/`、`ux04b6-20261105/`（各含 README + 命令日志 + `run-all.sh`）；`ux04c-20261105/`（第二、三批） | **由 `UX-04` 拆出**：S1 只交付「骨架 + 局部替换」，库组件目前仅在 `/dev/styleguide` 呈现，业务页仍是原生 `table`/`button`/`select`。迁移要逐页验四态、表格观感与打印版式，属独立可验收单元，混在 `UX-04` 里会让整包永远到不了 `待验`。**2026-11-05 已开工**：首批 5 处进业务页（`PlansView` 计划状态徽标 2 处 → `StatusTag`，`CasesView`/`AuditView`/`AdminView` 主列表空态 3 处 → `EmptyState`，并清理随之变成死代码的 `.badge*` 样式）。前置 `UX-04` 转 `待验`。**2026-11-05 第二批（两页整页迁移）**：`AuditView`（7 列表格 → `NDataTable` + `scroll-x` 横滚、`NSelect`×2 + `NDatePicker`×2、`NButton`、`NPagination`、空态走表格 `#empty` 槽）与 `ReportsView`（报告主表 + 清理清单双表、状态列 → `StatusTag`、工具栏与分页同上）。**顺带修真缺陷**：上一批三个 `EmptyState` **都没 import**，`<script setup>` 下未注册组件不报错、只渲染空白 —— 三页「无数据」时一片空，而构建/单测/lint 全绿；已补 import 并新增守门 `tools/check_component_imports.py`（模板 PascalCase 标签 ↔ import 对账，含递归组件与 Vue 内置白名单，带负向自检），接线进 `lint.sh`，上线 10 分钟内又抓出 `StyleguideView` 一处同款。新增 `runStatusTone()`（`utils/statusLabels.ts`）统一「运行状态 → 色档」，`StatusTag` 反向只 import 类型不引入环。**2026-11-05 第三批（两页整页迁移 + 视图级回归）**：`RunsView`（原生 `table` → `NDataTable` + `scroll-x`、`NSelect`、`NButton`、`NPagination`、状态列 → `StatusTag`、`NCheckbox`、时间走 `formatDateTime`）与 `CasesView`（筛选/表单 → `NInput`/`NSelect`，表格 → `NDataTable`，批量条 8 个按钮 → `NButton`，评审状态 → `StatusTag`）。**三个决策**：① 勾选列**不用**内置 `type: 'selection'` —— 它只认 `checked-row-keys`，`data` 变化会丢掉不在当前页的键，与 `useSelection` 的「跨页保留」（FR-v0.2-35）静默冲突，改为自渲染 `NCheckbox`；② 选中行底色用 `accent-soft` 而非固定的 `surface-selected`（后者是中性蓝，换金色/翡翠绿预设时与全站强调色不一致），并给「正在看日志的行」加 `row-active`；③ 新增 `reviewStatusTone()`，`draft` 用 `neutral` 而 `pending` 用 `warn`（同色会让待审项淹没在草稿堆里）。**首次建立视图级回归**：`CasesView.spec.ts` / `RunsView.spec.ts` 各 4 条（勾选接线、表头全选只作用本页、日志联动高亮只落对应行、状态双编码、终止行无取消），前端 47 → 61 条。**当时未完成**（已于第五批补齐）：报告详情页（`ReportDetailView`）仍为原生元素；打印页按约定保持零库组件。**2026-11-05 第四批（四页整页迁移）**：`AssetsView`（三表 → `NDataTable`、页签 → `NTabs`、端口面板改表格下方卡片、凭据关键词改前端过滤、换设备清空端口）、`PlansView`（表格 → `NDataTable`、表单 → `FormField` + 库控件、删窄屏卡片版）、`AdminView`（四表 → `NDataTable`、成员角色内联 `NSelect`、所有者行保持只读）、`OverviewView`（三表 → `NDataTable`、状态列 `badge` → `StatusTag`）。视图级回归 8 → **35**（6 spec），前端 61 → **88**。连带删除无消费者的 `.tabs`/`.tab` 样式并同步 `check_states.py`。**两个新踩坑**：① naive-ui 2.45.3 的 `NTabPane` 从 `children.tab` 取标题，只给 `:tab` prop 会渲染出三个**空白**页签且无报错；② `h(RouterLink)` 是模块导入组件，`app.component` 桩换不掉，视图测试必须模块级 mock `vue-router`。**2026-11-05 第五批（报告详情页整页迁移）**：`ReportDetailView` 10 个原生按钮 → `NButton`（含 `:loading`）、趋势用例下拉 → `NSelect`、日/周分段 → `NRadioGroup`+`NRadioButton`、对比开关 → `NCheckbox`、导出历史表与对比表 → `NDataTable`、对比汇总 4 计数 + 变化列 + 两侧结论 → `StatusTag`；新增 `verdictTone()`（`utils/statusLabels.ts`）。视图级回归 35 → **43**（7 spec，本页 8 条），前端 88 → **96**。**三条决策**：① **元数据键值表刻意保持原生 `<table>`** —— 它是 `th`+`td` 两列定义表而非数据网格，塞进 `NDataTable` 会凭空多一行表头并丢 `th` 语义，已加断言 `table.meta-table` + `tr`/`th` 各 10 个把这个决定钉死；② **「两轮都 FAIL」单独给 `changes.BOTH_FAIL` 文案**（中英各一条）—— 后端 `CompareService.changeOf` 只判"变没变"，两轮都 FAIL 归 `SAME`，界面显示「无变化」会被读成"通过且没变"，色档给 `warn` 而非 `fail`（本轮没有**新**坏，要与刚回归的分开）；③ 导出历史「内部版」由红字改 `warn` 徽标 —— 红色读起来像"导出失败"，而它表达的是**适用范围**。**新踩坑**：`NSelect`/`NRadioGroup` 的 `value` 是 `string\|number\|null`，`v-model` 到 `ref<'day'\|'week'>` 会被 `vue-tsc` 判不兼容，须 `:value` + 单一 setter 收敛；`NDataTable` 列宽是数字不带 `px` 不触发白名单，但模板内联 `style="width: 220px"` 会被扫到（宽度写 scoped class）。**连带**：注释新增 6 汉字触发 `subset_font.py --check` FAIL，子集 953 → **959 字 / 168,738 B gzip**。**当时未完成**（已于第六批补齐）：`SuiteTree`/`KeywordSteps` 仍为 `badge verdict-*` + 原生 `.tree-toggle`（**无 hover/focus**，且不在 `check_states.py` 扫描范围）；`EditorView`/`Login`/`NotFound` 未迁；打印页按约定保持零库组件。 **2026-11-05 第六批（套件树 + 守门扩范围）**：`SuiteTree` 套件/用例行状态 → `StatusTag`（新增 `verdictTone` 的消费点），**`.tree-toggle` 补齐 `:hover` / `:focus-visible` / `transition`**；`KeywordSteps` 步骤行**刻意保留紧凑文本徽标**（一屏几十行步骤，每行加 16px 图标会把步骤名挤成次要信息，且状态文字本就在徽标里）。**根因修复**：`tools/check_states.py` 原来只扫 `styles/*.css`，组件 `<style scoped>` 天生在盲区 —— `.tree-toggle` 三态全无而守门一直绿；现同时抽所有 `.vue` 的 `<style>` 块（`MIN_VUE_STYLE_FILES=5` 兜底）并把 `.tree-toggle` 登记进 `REQUIRED`，配 `neg-tree-toggle.py` 负向证据（剥掉 hover 必须报 FAIL）。**不用 `NButton` 换树行的理由**：库 `--n-height` 最小 28px vs 行高 21px，几十条用例会让树高翻倍，而该行本质是展开/收起控件（disclosure），缺的是三态不是缺组件。新增 `SuiteTree.spec.ts` 8 条（折叠/展开/emit/步骤递归/双编码/按钮语义），视图+组件回归 43 → **51**，前端 96 → **104**。**2026-11-05 第七批（业务视图迁移收尾）**：`LoginView`（`FormField` + `NInput` + `NButton attr-type="submit"`）、`NotFoundView`（`RouterLink custom` + `NButton`）、`OverviewView`（第四批漏网的刷新按钮）、`EditorView`（5 个 `.editor-host__btn` → `NButton`），并删 3 条登录页死样式 + 4 条编辑器页无三态样式。**测试抓到两个"看起来正常"的真回归**：`NInput` 不透传顶层 `autocomplete`（须进 `:input-props`，否则密码管理器线索静默丢失）；`NButton` 的 `type` 是语义色档、提交行为要 `attr-type="submit"`（写错误表现为「能点不能回车」）。新增 `LoginView.spec.ts` 5 条，视图+组件回归 51 → **56**，前端 104 → **109**。**至此 12 个业务视图全部完成组件化**，`ReportPrintView` 按约定零库依赖、`StyleguideView` 本身即设计系统展示页。**转 `待验` 的唯一缺口**：登录后四态人工截图（需测试账号 + 可达 dev server，属待现场项）。 |
| 81 | **UX-04c** | 侧边栏信息架构两级化：6 个一级项平铺 → 分组 + 子项（资产 / 执行 / 管理三组可折叠，单页模块不立空壳组）；选中态改强调色三重编码；菜单结构与路由的绑定加守门 | B3 | 待验 | `docs/evidence/v0.3/ux04c-20261105/`（README + 12 条命令日志 + `run-all.sh`）、`frontend/src/config/navigation.spec.ts` | **2026-11-05 大师定「菜单最好是 2 级，后续功能逐步变多」+「当前选中项要重点色强调」**。结构抽成唯一真源 `config/navigation.ts`（`NavGroup`/`NavLeaf` + `isNodeActive`/`visibleNav`/`filterNav`），选中判定不再依赖 `router-link-active`（资产三页共用一个 path，靠 `?tab=` 区分，浏览器默认规则会把三个子项同时点亮）；`AssetsView` 页签与 URL 双向同步，支持深链与前进后退。选中态＝左侧 3px 强调色竖条 + `accent-soft` 底 + `accent` 文字（对比度由六预设派生保证），仅换底色时暗色下与 hover 分不出来。展开态落 `localStorage`，进入某组子页自动展开。**权限修正**：`管理` 组不设 `adminOnly`（该页含「项目成员」，PM/所有者也要用），只挡审计子项 —— 整组隐藏会造成功能回归。守门新增 `navigation.spec.ts` 12 条（叶子路径必须能被路由解析、不允许单子项组、query 精确点亮、前缀边界、权限过滤、空组自动丢弃） |
| 82 | **UX-04d** | 土豪金预设调优：亮色主色顶到 WCAG 亮度上限 + 手调金色 tint（`SOFT_OVERRIDE`）；暗色转更饱和亮金 | B3 | 待验 | `docs/evidence/v0.3/ux04c-20261105/accent-tokens.log`、`check-contrast.log` | **大师定「土豪金颜色可适当优化」**。先把约束算清楚：亮色金被 `accent/soft ≥ 4.5` 钉死，`#8a6300` 时余量只有 0.23 —— 靠「调亮主色」这条路口本走不通。真正的病根是机械派生 tint：暗金 × 10% + 白 = `#f3efe6`，一块几乎无彩的米灰，选中行看着像灰色。改为手调金色 tint `#fdf3d6`（仍是金），`accent/soft` 反升到 4.91，于是主色得以提亮到 `#8f6500`（5.21 / 4.71，已到上限，再浅一档即 4.43 FAIL）。暗色 `#f5c542 → #ffd24a`（13.00 / 10.63 / 8.78），tint 换暖金 `#3a3222`。`SOFT_OVERRIDE` 只对 gold 生效且**不豁免任何判据**，改错直接 FAIL；色板展示色同步 `--preset-gold`。14 组 280 项全部重算通过 |
| 83 | **EXEC-02** | 勾选台账用例直接执行：`[Tags] case:<code>` 绑定解析 + 测试级 `--test` 透传（v0.4 补充包，2026-11-05 并入） | B4 | 待验 | `docs/evidence/v0.4/exec02/`（README + 9 份日志）；实现 `CaseScriptResolver`、`CaseRunService`、`POST /api/execution/cases/run`、`PabotCommandBuilder` 透传 `--test`、CasesView「执行所选」表单 | `--test` 实跑效果待验收人现场确认（ADR-0013 禁跑令）；绑定读 DB 与执行器读磁盘的工作区差异仍在 |
| 84 | **EXEC-03** | 用例集测试级执行：`TestSetRunService` 解析绑定后透传 `testNames`，集合只跑所选测试（v0.4 补充包，2026-11-05 并入） | B4 | 待验 | `docs/evidence/v0.4/exec03/`（README + 5 份日志）；`TestSetRunService` + 2 个单测 | `--test` 实跑效果待验收人现场确认（ADR-0013）；空编号用例原静默跳过，EXEC-04 已改 400 |
| 85 | **EXEC-04** | 执行前预检：套件路径存在性、用例编号非空、测试名跨套件重名，命中直接 400（v0.4 补充包，2026-11-05 并入） | B4 | 待验 | `docs/evidence/v0.4/exec04/`（README + 5 份日志）；新增 `ExecutionRequestPrecheck`、`QuickRunService` 接入、用例集空编号 400、5 个预检单测 | 工作区需先同步脚本（默认 `~/robot-test/infra-monitor`）；目录路径不查重名 |
| 86 | **EXEC-05** | 执行工作区落盘：`robot_file.content` 按路径写入 `<artifacts>/<runId>/workspace`，pabot 以该目录为工作目录 （v0.4 补充包，2026-11-05 并入） | B4 | 待验 | `docs/evidence/v0.4/exec05/`（README + 6 份日志）；新增 `ExecutionWorkspaceMaterializer`、`RobotFileRepository#listAll`、`PabotCommandBuilder` 工作目录重载、`RunExecutionService` 接入、请求期预检改 DB 优先/磁盘回落；9 个新增单测 | 数据库无脚本时仍回落 `execution.workspace-root`（兼容计划/CI）；真实 pabot 在落盘工作区执行待现场确认（ADR-0013） |
| 87 | **EXEC-06** | 全量日志下载：`GET /api/execution/runs/{runId}/log/download` 返回 `run.log` 附件；运行页日志面板加「下载完整日志」（v0.4 补充包，2026-11-05 并入） | B4 | 待验 | `docs/evidence/v0.4/exec06/`（README + 9 份日志）；新增 `RunLogFileService`（定位与 404 口径）、控制器端点、1 接口测试 + 3 服务测试；前端 `downloadLog` + 按钮 + 1 视图测试 | 实时频道仍只保留最近 2000 行（完整日志走文件下载）；`run.log` 不存在或产物已清理时 404 |
| 88 | **EXEC-07** | 租约可视化与管理员强制释放：资产页新增「租约」只读页签（资源 / 运行 / 剩余时间 / 状态，`FR-v0.4-09`）；行内「强制释放」仅管理员可见，二次确认以 `runId` 作 confirm，后端把原运行标记为 `failed(lease_revoked)` 并释放租约（`FR-v0.4-10`，v0.4 补充包，2026-11-05 并入） | B4 | 待验 | `docs/evidence/v0.4/exec07/`（README + 9 份日志）；新增 `LeaseAdminService`、`POST /api/execution/leases/force-release`、4 条服务测试 + 1 条接口测试；前端 `listLeases` / `forceReleaseLease` + 资产页租约页签 + 4 条视图测试 | 强制释放只摘租约、不杀子进程（残余写进 javadoc）；租约列表只列 `lease_state = held`，历史租约需查审计 |
| 89 | **EXEC-08** | v0.4 PRD 34 条 FR 逐条覆盖复核：按「覆盖 / 部分 / 缺失 / 待现场」四档给出实现位置、测试证据与缺口，产出 `docs/release/v0.4-execution/FR-COVERAGE-20261105.md`（20 覆盖 / 12 部分 / 0 缺失 / 2 待现场），并排出 7 条补口优先级（首位为租约接入执行链路） | G | 待验 | `docs/evidence/v0.4/exec08/`；`FR-COVERAGE-20261105.md` 逐条 FR 对照表 + 缺口排序 | 复核包，不改实现、不计入 v0.4 交付包数（`release/README.md` 仍为 15）；缺口待另开包 |
| 90 | **EXEC-09** | 租约接入执行链路（FR-04/07/09/10/23）：进入 RUNNING 前按 run 维度申请租约，设备键优先（如 `DUT-01/mlx5_0`），未声明设备范围则用 `run:<runId>` 自持（工作区/端口已按 run 隔离，不与其他 run 互斥）；申请失败直接 `failed(lease_timeout)`（INFRA，不启动 pabot）；成功 / 失败 / 异常 / 取消 / 超时 / 重启统一走 `releaseRun`；续约沿用 40s 全局心跳 | G | 待验 | `docs/evidence/v0.4/exec09/`；`RunExecutionServiceTest` 新增 5 条（执行期持有 / 成功释放 / 失败释放 / 占用超时不启动 / 键退化） | 租约按 run 聚合而非按分片拆 job（`execution_job` 每 run 一条）；FR-08 等待态 UI（WAITING）仍缺；`lease_wait_ms` 未回写 |
| 91 | **EXEC-10** | 队列语义收口（FR-03/05）：派发器由全局单线程改为「按项目分道 + 单项目 `MAX_PARALLEL` 并发」，同项目按 priority 降序、再按提交先后排队，达上限只排队不拒绝；不同项目分属不同队列与线程池、互不阻塞；runId 在途去重保留 | G | 待验 | `docs/evidence/v0.4/exec10/`；`ProjectRunDispatcherTest` 4 条（优先级顺序 / 上限排队不拒绝 / 跨项目并行 / 在途去重） | 优先级只在派发队列内生效，未回写 `execution_job.queue_state`；`QUEUED` 队列位次接口（pending-activity 语义）未做 |
| 92 | **EXEC-11** | 设备范围落库与回溯（FR-v0.4-01）：新增 `run.device_scope`（`V7_4` 迁移 + down），创建时按 `deviceKeys` 去重保序落库（≤512 字符；空/全空白 = NULL，不自造键），`RunRecord` / JDBC 映射 / 前端类型全链路回溯；手动执行表单加「设备范围」多选（可手填端口键），日志面板显示当前运行的设备范围 | G | 待验 | `docs/evidence/v0.4/exec11/`；后端 `QuickRunServiceTest` 3 条（落库回溯 / 缺省 NULL / 超长 400）、`MigrationFileTest` V7_4 断言、前端 `RunsView.spec` 2 条（提交带 deviceKeys / 面板展示） | 并发度仍是数字输入（1~`MAX_PARALLEL`），未改 PRD 的 1/2/4 三档；运行**列表**未加设备范围列（只在日志面板展示）；执行期资源键仍按请求 `deviceKeys` 直取，未从 `device_scope` 反解 |
| 93 | **EXEC-12** | 单用例超时（FR-v0.4-24）：`execution.test-timeout-seconds`（默认 1800，`<=0` 关闭）+ 执行前向本 run 工作区副本的 `.robot` 套件 `*** Settings ***` 注入 `Test Timeout`（RF 7.5 无全局 CLI 开关；用户已显式写过则保留原值）；产物检出 `Test timeout ... exceeded` 时 run 原因记 `test_timeout`（按取消归类 CANCELED） | G | 待验 | `docs/evidence/v0.4/exec12/`；`TestTimeoutInjectorTest` 5 条、`ArtifactVerifierTest` 1 条；后端 539 / 集成 28 / 前端 162 / `vue-tsc`+`build` 0 / `lint.sh` PASS | `.resource` 资源文件不注入；未做「按用例单独配置超时」与「UI 可调」；注入只发生在 materialize 成功的副本上（回落 workspace-root 时不注入，避免改用户原脚本） |
| 94 | **EXEC-13** | 运行详情重试可追溯（FR-v0.4-29）：日志面板在 `attemptNo > 1` 时显示「第 N 次尝试」（`execution.attemptLine`），首次尝试不渲染；配合列表既有「失败原因 / 状态」列，构成「第几次 / 原因 / 结果」三件可追溯信息 | G | 待验 | `docs/evidence/v0.4/exec13/`；前端 `RunsView.spec` 1 条（第 2 次显示 / 首次不显示）；后端 539 / 集成 28 / 前端 163 / `vue-tsc`+`build` 0 / `lint.sh` PASS | `retry_of_run_id` 已入库但未对外暴露，运行详情看不到「上一次失败原因」；要显示需在 `RunRecord` / `RunView` 增字段（23 处构造点） |
| 95 | **EXEC-14** | 日志级别过滤（FR-v0.4-20）：日志面板把单档「只看失败」换成 ERROR / WARN / INFO 三个可独立勾选的档位（默认全开，未知级别按 INFO 放行）；三档全关时明说「当前级别过滤下没有日志」，而不是假装这份 run 没日志 | G | 待验 | `docs/evidence/v0.4/exec14/`；前端 `RunsView.spec` 1 条（取消「信息」后 INFO 行隐藏、ERROR/WARN 保留、全关空态文案）；后端 539 / 集成 28 / 前端 164 / `vue-tsc`+`build` 0 / `lint.sh` PASS | 后端仍只产出 ERROR/WARN/INFO 三档（PRD 里的 FAIL 归 ERROR、DEBUG 未产出）；要真正的 DEBUG 档需在执行器侧采集 RF 的 DEBUG 日志，属 v0.5 日志补口 |
| 96 | **EXEC-15** | 执行安全级闸门（ADR-0013 / G2-3）：`test_case.safety_level`（`V7_5` 迁移 + down，缺省 `LX`）落库并全链路回溯；执行入口两处（执行所选 `CaseRunService` / 执行用例集 `TestSetRunService`）逐条判级，**L2 高危与 LX 未判定一律整单拒绝**（403 `SAFETY_LEVEL_BLOCKED`，消息列出前 10 条 `code(level)`）；新增批量动作 `setSafetyLevel` 作人工补判；列表加安全级列与筛选，选中含 L2/LX 时前置禁用「执行所选」并写出用例编号 | G | 待验 | `docs/evidence/v1.0/g2-3/`（含本机 HTTP 实测 `http-verify-20261105.md`：`L2`/`LX` → 403、`L9` → 400）；后端 `CaseRunServiceTest` 2 条（L2 被拒 / LX 补判后放行）、`TestSetRunServiceTest` 集合级拦截、`CaseBatchIT`（落库 + 非法值 400）、`CaseImportIT`（无安全级列 → LX）、`MigrationFileTest` V7_5；前端 `statusLabels.spec` 3 条、`CasesView.spec` 1 条（L2 禁用执行） | 跨页选中的安全级前端不可见，只由后端整单拒绝兜底（要准需后端预检接口）；台账无安全级列，226 条导入后全为 `LX`，需人工逐条补判才能执行——这正是 ADR-0013 想要的默认态 |

---

## 交接三问（每次开工前先答，答不上就别开工）

1. **上一个完成的是什么、证据在哪？**（在下面「最近完成」里找一行，点开证据链接）
2. **下一个该做哪个？**（按 §6.3 依赖关系表挑：前置已 `已完成` 且本包 `未开始`；**ENV-01~05 已由大师裁决转 `已完成`，环境阻断清零 → 当前动作是按门禁 G1 → G2 成组核验（F1）**，不再有「因环境缺失而全停」的包）
3. **有没有卡住的？**（状态为 `进行中` 且超过两次会话没推进的，必须写清「需要谁做什么」；`待现场` 的必须写「需谁、需什么设备、约多久」）

## 最近完成（新的在上，保留最近 10 条）

| 日期 | WBS | 一句话结果 | 证据 |
|---|---|---|---|
| 2026-11-05 | IAM-02（缺口修复） | **补上 FR-v0.2-04 会话续期**：后端新增 `POST /api/auth/renew`（`AuthService.renew` 校验未撤销令牌 → `SessionTokenRepository.extend` 推 `expires_at` 一个 TTL 并记 `last_used_at`），前端 `stores/auth.ts` 持久化 `auth_expires_at`、提前 5 分钟 `setTimeout` 续期、失败即 `clear()`；新增 `AuthServiceRenewTest` 3 条。**G2-3 L2 执行拦截经查为产品缺口**（全仓无 `safetyLevel/L2` 运行时校验、`test_case` 无安全级字段、分级只在离线 `tools/classify_cases.py`），已记入 G2 预检，未擅自扩数据模型。全量回归：`lint.sh` 0 / 后端 `mvn -B test` 0（含新增）/ IT 0 / 前端 `181 passed` / `npm run build` 成功 | `backend/src/main/java/com/qa/autotest/{application/iam/AuthService.java,infrastructure/SessionTokenRepositoryJdbc.java,api/iam/AuthController.java}`、`frontend/src/stores/auth.ts`、`frontend/src/api/iam.ts`、`docs/evidence/v1.0/g2-precheck/` |
| 2026-11-05 | UX-04b(8) | **UX-04b 转 `待验` + 修掉 WBS 汇总与逐行状态不一致（新增守门 `check_wbs_consistency.py`）**。`UX-04b` 七批全部落库、12 个业务视图组件化完成，状态 `进行中 → 待验`（`待验 → 已完成` 仍只能人改），分组表「进行中」清零。**修的真缺陷**：F2 裁决把 8 个 `待现场` 包授权代改为 `已完成`，但只改了汇总行 / 分组表 / 修订记录三处，**8 个单包行的状态格没动** —— 汇总显示「待现场 0 / 已完成 13」，逐行数出来是「待现场 8 / 已完成 5」。已用脚本按 F2 裁决逐行改正（`EDT-08 / EXE-07 / EXE-08 / PLN-03 / PLN-04 / HRD-03 / HRD-04 / DOC-03`），并在各行收尾列追加「授权代改」出处说明（原文待现场口径保留以便追溯），现文档自洽：**82 包 / 未开始 3 / 进行中 0 / 待验 66 / 待现场 0 / 已完成 13**。**防复发**：新增 `tools/check_wbs_consistency.py`（四项机检：状态值合法、汇总五数 = 逐行分布、当前进度 N/M 一致、单包行列数 = 7）并接线进 `lint.sh` —— 这类"汇总骗人"的问题人眼对着几百行表格发现不了。 | `docs/evidence/v0.3/ux04b6-20261105/`（含 `apply-f2-decision.py` / `annotate-f2-rows.py` 两个一次性脚本 + 日志） |
| 2026-11-05 | UX-04b(7) | **登录 / 404 / 编辑器收尾 —— 业务视图组件化迁移完成（视图+组件回归 51 → 56、前端 104 → 109）**。`LoginView`：两个原生 `<input>` → `FormField` + `NInput`、提交按钮 → `NButton`；`NotFoundView`：`RouterLink class="btn"` → `RouterLink custom` + `NButton`（导航归路由、视觉归按钮）；`OverviewView`：第四批漏网的刷新按钮；`EditorView`：5 个 `.editor-host__btn` → `NButton`（primary+loading / small / tiny），删 4 条三态全无且用 `rem` 的样式。**测试抓到两个"看起来正常"的真回归**：① `NInput` 不把顶层 `autocomplete` 透传到内部 `<input>`（须写进 `:input-props`），密码管理器线索静默丢失而页面完全正常；② `NButton` 的 `type` 是语义色档，HTML 提交行为要 `attr-type="submit"` —— 写错误表现为「点按钮能提交、回车没反应」。删 `.login-card .field`/`.field-label`/`.login-card .input` 三条死样式。新增 `LoginView.spec.ts` 5 条。**lint 中断一次并如实记录**：mock 写成 `async (_u, _p) => …` 被 `@typescript-eslint/no-unused-vars` 拦（`_` 前缀不豁免），改泛型签名 `vi.fn<(u: string, p: string) => Promise<void>>()`。实测：视图+组件 **56/56**、前端 **109/109**、后端 **504/0/0**、`vue-tsc` 通过、`lint.sh` 0 FAIL、vendor 105,148 B、字体 967 字 / 169,974 B | `docs/evidence/v0.3/ux04b6-20261105/`（README + 13 条命令日志 + `run-all.sh`） |
| 2026-11-05 | UX-04b(6) | **套件树迁 `StatusTag` + 补齐交互三态 + 守门扫描范围扩到 `.vue` scoped style，视图+组件回归 43 → 51 条、前端 96 → 104 条**。`SuiteTree`：套件/用例行 `badge verdict-*` → `StatusTag`；`.tree-toggle` 补 `:hover` / `:focus-visible` / `transition`（原来裸 `<button>` 三态全无，键盘走一遍看不出焦点停在哪行）。**根因修复**：`check_states.py` 原来只扫 `styles/*.css`，组件 `<style scoped>` 天生在盲区 —— 这就是「缺陷在、守门绿」的来源；现同时抽所有 `.vue` 的 `<style>`（`MIN_VUE_STYLE_FILES=5` 兜底），`.tree-toggle` 进 `REQUIRED`，并配 `neg-tree-toggle.py` 负向证据（剥掉 hover 必须报 FAIL），因为「扩了范围」本身没有可观测产出，不注入缺陷就无法证明它不是假绿。**不用 `NButton`**：库 `--n-height` 最小 28px vs 树行 21px，几十条用例会让树高翻倍；该行是 disclosure 控件，缺的是三态不是缺组件。`KeywordSteps` 步骤行**刻意保留紧凑徽标**（密集次要信息，图标反噪）。新增 `SuiteTree.spec.ts` 8 条。实测：视图+组件 **51/51**、前端 **104/104**、后端 **504/0/0**、`lint.sh` 0 FAIL、vendor 105,091 B、字体 964 字 / 169,534 B | `docs/evidence/v0.3/ux04b5-20261105/`（README + 14 条命令日志 + `run-all.sh` + `neg-tree-toggle.py`） |
| 2026-11-05 | UX-04b(5) | **报告详情页整页迁库组件，视图级回归 35 → 43 条、前端 88 → 96 条**。`ReportDetailView`：10 个原生按钮 → `NButton`（四个导出按钮带 `:loading`）、趋势用例下拉 → `NSelect`、日/周分段 → `NRadioGroup`+`NRadioButton`（原 `.active` 只换边框色，键盘与读屏都拿不到选中项）、对比开关 → `NCheckbox`（`@update:checked` 直接给布尔）、导出历史表与对比表 → `NDataTable`（对比表自带横滚，删掉窄屏 `display:block` 那套双份渲染）、对比汇总 4 计数 + 变化列 + 两侧结论 → `StatusTag`；新增 `verdictTone()`。**元数据键值表刻意保持原生 `<table>`**（`th`+`td` 两列定义表，进 `NDataTable` 会多一行表头并丢 `th` 语义），已加断言钉住。**修掉一处会读反的文案**：后端 `changeOf` 只判「变没变」，两轮都 FAIL 归 `SAME` → 界面显示「无变化」会被读成「通过且没变」，新增 `changes.BOTH_FAIL`「持续失败 / Still failing」，色档 `warn` 而非 `fail`（本轮没有**新**坏）。另：导出历史「内部版」由红字改 `warn` 徽标（红色像"导出失败"，实际是**适用范围**）；外部导出禁用提示从 5 处重复收敛为单个 `externalHint`。**新踩坑**：`NSelect`/`NRadioGroup` 的 `value` 是 `string\|number\|null`，`v-model` 到 `ref<'day'\|'week'>` 被 `vue-tsc` 判不兼容 → `:value` + 单一 setter；`NDataTable` 列宽是数字不带 `px` 不触发白名单，但模板内联 `style="width: 220px"` 会被扫到（宽度写 scoped class）。**连带**：注释新增 6 汉字触发字体子集 FAIL，953 → **959 字 / 168,738 B gzip**。实测：视图 **43/43**、前端 **96/96**、后端 **504/0/0**、`lint.sh` PASS、vendor 105,080 B、懒加载分包 100,855 B | `docs/evidence/v0.3/ux04b4-20261105/`（README + 12 条命令日志 + `run-all.sh`） |
| 2026-11-05 | UX-04b(4) | **资产 / 计划 / 管理 / 概览四页整页迁库组件，视图级回归 61 → 88 条**。`AssetsView`：三张原生表 → `NDataTable`、页签 → `NTabs`、端口面板从「行内展开」改为「表格下方卡片」（与执行页日志面板同一交互语言）、凭据关键词改**前端过滤**（后端 `GET /credentials` 无关键词参数，原来在凭据页签搜索是个静默无效控件）、换 A 端/ B 端设备时**清空已选端口**（旧实现只重载列表不清 `aPortId`，会提交「设备是 B、端口是 A」的错链）。`PlansView`：表格 → `NDataTable`、表单 → `FormField` + `NInput`/`NInputNumber`/`NSelect`/`NCheckbox`、**删掉窄屏卡片版**（`scroll-x` 已够用，双份 markup 等于双份维护）。`AdminView`：四张表 → `NDataTable`、成员角色内联 `NSelect`（所有者行保持只读文本，后端会拒）、停用用户统一说「已停用」。`OverviewView`：三张表 → `NDataTable`、状态列从**只有颜色**的 `badge verdict-*` 换成 `StatusTag`（图标 + 文字双编码，解决六预设与状态色同色相）。**连带**：原生 `.tabs`/`.tab` 无消费者后删除，`check_states.py` 同步移除该条（注释说明由库主题接管）。**新发现**：naive-ui 2.45.3 的 `NTabPane` 从 `children.tab` 取标题，只给 `:tab` prop 会渲染出**三个空白页签且无任何报错**；`h(RouterLink)` 在未装 router 的测试环境会崩在 vue-router 内部，视图测试必须模块级 mock。 | `docs/evidence/v0.3/ux04b3-20261105/`（README + 12 条命令日志 + `run-all.sh`） |
| 2026-11-05 | UX-04b(3) | **运行 / 用例两页整页迁库组件，并首次建立视图级回归**。`RunsView`：原生 `table` → `NDataTable` + `scroll-x`、`NSelect`/`NButton`/`NPagination`、状态列 → `StatusTag`、时间列走 `formatDateTime`；`CasesView`：筛选与表单 → `NInput`/`NSelect`、表格 → `NDataTable`、批量条 8 个按钮 → `NButton`。**勾选列刻意不用内置 `type: 'selection'`** —— 它只认 `checked-row-keys`，`data` 变化会丢掉不在当前页的键，与「跨页保留」（FR-v0.2-35）静默冲突；改为自渲染 `NCheckbox`，状态仍由 `useSelection` 持有。选中行底色改 `accent-soft`（`surface-selected` 是固定中性蓝，换金色/翡翠绿预设与全站强调色不一致），并补 `row-active` 标出「正在看日志的那行」。新增 `reviewStatusTone()`：`draft` 用 `neutral`、`pending` 用 `warn`，避免待审项淹没在草稿堆里。**视图级回归**（此前只有 composable 层）验证了 `NDataTable` 在 happy-dom 下可渲染，两个 spec 各 4 条，前端 47 → **61**。实测：视图 8/8、后端 **504/0/0**、`lint.sh` PASS、vendor 104,957 B、懒加载分包 97,028 B、字体 935 字 | `docs/evidence/v0.3/ux04b2-20261105/`（README + 12 条命令日志 + `run-all.sh`，负向自检 rc=1） |
| 2026-11-05 | **F2 裁决** + UX-04c + UX-04d + UX-04b(2) | **F2 验收通过（大师裁决，授权代改）**：8 个 `待现场` 包全部转 `已完成` —— `EXE-07`（4 并发 20 轮）、`EXE-08`（DUT 实网）、`EDT-08`（许可签字）、`PLN-03`（GitHub token）、`PLN-04`（双仓决策签字）、`HRD-03`（试点 + 新人操作）、`HRD-04`（回滚 RTO）、`DOC-03`（IA 签字），`待现场` 清零；`UX-04` 的现场观感项同批通过。**同轮三条开发**：① 侧边栏两级化（#81）—— 菜单结构抽唯一真源 `config/navigation.ts`，选中判定改 path+query（资产三页靠 `?tab=` 区分，`router-link-active` 会三选三亮），选中态用强调色三重编码，页签与 URL 双向同步，权限只挡审计子项（`管理` 页含项目成员，整组隐藏是回归）；② 土豪金调优（#82）—— 先算清亮色金被 `accent/soft≥4.5` 钉死（余量 0.23），真正病根是机械 tint 变米灰，改手调暖金 tint 后主色才提得动：亮 `#8f6500`（5.21/4.71）、暗 `#ffd24a`（13.00/8.78）；③ 视图迁移第二批（#80）—— 审计、报告两页整页交 `NDataTable`/`NButton`/`NDatePicker`，并修掉上一批 **3 处 `EmptyState` 漏 import** 的静默空白（构建/单测/lint 全绿但页面空白）。**新增两条守门**：`check_component_imports.py`（模板组件 ↔ import，带负向自检）、体积预算「懒加载共享分包 ≤120 KiB」（NDataTable 的 92 KiB 原本在预算之外）。实测：前端 47/47、菜单 12/12、后端 **504/0/0**、对比度 280 项、`lint.sh` PASS、字体 928 字 | `docs/evidence/v0.3/ux04c-20261105/`（README + 12 条命令日志 + `run-all.sh`，含负向自检 rc=1） |
| 2026-11-04 | PRJ-04 | **报告结果导向落地**：新增只读接口 `GET /api/projects/{id}/report-digest`（`days` 夹取 1~90），返回最近完成报告 / 失败 Top10 / 覆盖率（已覆盖 + 从未执行）/ 通过率趋势；首页新增「报告结果」区块（覆盖事实行 + 通过率柱 + 最近报告表 + 失败 Top10 空态）。**口径**：只统计 `finished/failed`；无明细 → `null`（不显示 0%），全 SKIP → 0；覆盖率按 `case_code = case_key` 精确匹配，属缺口上界，界面写明「脚本步骤名不计入」。**接口与 SQL 逐项对账一致**（1 份报告 / 2 PASS / 0 FAIL / 226 未执行 / 趋势 1 点）。`mvn test` **466** 全绿（新增 3 条），四个守门脚本全过 | `docs/evidence/v0.2/prj04/`（`README.md`、`prj04-api.log`、`prj04-checks.log`、`01/02` 亮暗截图） |
| 2026-11-04 | PRJ-01 | **项目中心成员管理落地**：管理页新增「项目成员」区块（选项目 → 添加 → 改角色 / 移除），owner 行显示「管理员 · 所有者」并锁定操作；`ProjectPicker` 补无项目引导。**owner 保护**：移除/降级返回 **409**（原为 400），普通成员增改删 **200** 作对照，新增 4 条边界用例（`mvn test` **463** 全绿）。`ProjectMember` 扩 `displayName`/`enabled` 以便标「已停用」。**现场两个基线问题**：演示库 `owner_id` 为 NULL、`member_role` 还是 V1 的 `owner`（`RoleCode.from("owner")` 会抛异常）→ 一次性数据归一后才接上保护；另外角色下拉与「所有者」标记贴边，补 `.member-role` | `docs/evidence/v0.2/prj01/`（`README.md`、`prj01-api.log`、`prj01-checks.log`、`01/02` 亮暗截图） |
| 2026-11-04 | UX-01b + EXE-03 修复 | **界面基线修复（美术 S0）落地**：`--muted` 21 处改 `--text-muted`；六类元素补 hover/focus-visible + `*:focus-visible` 2px 焦点环；`.btn.danger` / `.btn-ghost.danger`；`.app-body` 限宽 1440 + 5 张宽表容器横滚；`check_contrast.py` 改为按 **(主题 × 强调色)** 分组（原实现被变体块覆盖默认值 → 假绿），新增 `check_tokens.py`（悬空 var + px 白名单）与 `check_states.py`（三态 + transition），均带破坏性自检。**顺带修一个真缺陷**：`PabotVersionProbeImpl` 执行 `pabot --version` 取到 `linux)` 与期望 `5.2` 比较，导致**所有运行在预检被永久拒**；改读包元数据后，本机触发 `RUN-20261104155814-952426` 真跑通（`FINISHED 2/2`，产物 `shard-0/output.xml`，`/api/reports/{runId}/parse` 入库 `pass=2`）。**教训**：`bash -c` 经 PowerShell 调用时 `$HOME` 会漏成 Windows 路径，启动脚本必须写绝对路径 | `docs/evidence/v0.2/ux01b/`（`README.md`、`ux01b-checks.log`、`ux01b-frontend.log`、`backend-mvn-test.log`、13 张截图） |
| 2026-11-04 | UX-01 | **亮/暗双主题落地**：113 处硬编码色值清零（main.css 35 + 组件 78），两层 token（primitives + semantic）支撑三套状态；三态切换 system/light/dark 全部实测生效，system 下改 OS 外观**无刷新即跟随**；首帧 3ms 写入 data-theme（不闪）；`user_preference` 表 + `GET/PUT /api/users/me/preferences` 实现换设备一致。**顺带修两个既有缺陷**：暗色表格边框原值 `#22303f` 与卡片只差 1.14:1（分割线几乎看不见）；`scripts/db-audit.sh` 被写成 CRLF，Linux 下 `set -o pipefail` 直接报错跑不起来 | `docs/evidence/v0.2/ux01-checks.log`、`ux01-theme-probe.log`、`ux01-{light,dark}/`（7 页截图）、`ux01-frontend.log` |
| 2026-11-04 | UX-03 | **国际化一致落地**：EditorView 14 处（计划写 24，盘点偏差）硬编码中文抽键；五组枚举集中 `statusLabels.ts`（下拉选项同源）；错误统一 `ErrorLine.vue`（主文案走 `error.<NAME>` 键、服务端原文+codeName 进折叠，覆盖 12 个 view）；`format.ts` 替换 4 处手写实现；守门脚本 5 项检查 + 3 轮破坏性自检。**计划外**：后端总览接口去中文哨兵「未标注」（英文界面必冒中文），IT 锁契约。中英双界面 22/22 断言过、IT 16/16、单测 459。**教训**：走查前必须清点遗留 vite/后端进程（本轮旧进程产过一轮作废证据）；本机 curl 被 HTTP_PROXY 劫持须 `--noproxy '*'`；vite 自测要 `--host 127.0.0.1` | `docs/evidence/v0.2/ux03-i18n/`、`ux03-i18n-walkthrough.log`、`ux03-format.log` |
| 2026-11-04 | 计划（新增 5 包） | **WBS 增 5 包**（#72–76）：`UX-01b`（美术 S0 基线修复，v0.2 收尾必做）、`EXEC-01`（人工触发执行 + 僵尸端点收编，含挂起的执行队列决策）、`UX-04/05/06`（美术 S1–S3）。美术计划按调研推荐 A 编制，5 项待拍板（组件库/字体/品牌/版本归属/强调色）已列改判影响；EXEC-01 评估实锤三链路现状（唯一能跑的是计划触发，`POST /execution/runs` 僵尸、台账↔脚本无绑定）。**只写计划未开发**；**运行时迁 WSL**（同日，独立决策见 docs/env/） | `docs/design/美术体系-开发与测试计划-20261104.md`、`docs/plan/执行人工触发-评估与计划-20261104.md`、`docs/release/v0.2-ledger/{PRD,DEV-PLAN,TEST-PLAN}.md` 增量、`docs/env/WSL运行时迁移-评估与实施-20261104.md` |
| 2026-11-04 | NAM-01 | **产品改名落地**：中文「AIDC自动化测试」/ 英文「AIDC AutoTest」，登录页与侧栏品牌位、浏览器标签页（随语言切换）全部生效；顺带修掉 `index.html` 静态标题不随语言变的问题。**工程标识零改动**（pom / package.json / Java 包名 / 库名均未动） | `docs/evidence/v0.2/nam01-naming.log`、`nam01-zh/overview.png`、`nam01-en/overview.png` |
| 2026-11-04 | 计划（新增 9 包） | **v0.2 增 9 个包**（`NAM-01`、`UX-01~03`、`PRJ-01`/`PRJ-03`/`PRJ-04`、`AST-05`、`UX-02b`）：双主题、列表批量、中英一致与命名、项目中心与资产共享池。四项决策经向导确认（跟随系统 / 资产池+引用 / 用例优先 / 并入 v0.2）。已完成评估 + PRD `FR-v0.2-31~53` + 开发计划 §10 + 测试计划 §13，**代码未动** | `docs/plan/多项目与体验基线-评估与设计-20261104.md`、`docs/release/v0.2-ledger/{PRD,DEV-PLAN,TEST-PLAN}.md` |
| 2026-11-04 | OVW-01（新增） | **总览从 43 行 ping 占位页改成真总览**：一次聚合接口返回用例数 / 资产数 / 执行统计 / 近期执行 / 近 30 天趋势；读数与直接 SQL **13 项逐项一致**（226 用例、3 设备、2 链路、2 凭据、0 运行）；空项目 `passRateBp=null` 而不是 0；跨项目 403。顺带修掉 `TrendRepositoryJdbc` 把状态写成 `IN ('passed','failed')` 的死条件（枚举里没有 `passed`，`finished` 的 run 全被漏算） | `docs/plan/总览页真实化-评估与设计-20261104.md`、`docs/evidence/v0.2/{ovw01-db-vs-api.log, ovw01-api.json, ovw01-repo-it.log, ovw01-mvn-test.log, ovw01-frontend.log, frontend-ovw01/overview.png}` |
| 2026-11-04 | CAS-01/02/03（前端） | **前端首次真跑通**：登录 → 6 页逐页核对，抓到一个会让验收误判的缺陷——用例 / 资产 / 执行三页的 `watch` 缺 `immediate`，`projectId` 初值即目标值时 `load()` 从不执行，页面永远「暂无数据」且**不发任何请求**。修后 `/api/projects/1/cases` 与 `/devices` 正常发出，226 条台账渲染为 23 页、3 台设备正常显示 | `frontend/src/views/{Cases,Assets,Runs}View.vue`、提交 `c79dbb5`、`docs/env/环境基线.md` §10 |
| 2026-11-04 | G1 判据 | 后端**真启动**通过（此前 450+ 单测从未起过 Spring 上下文）：空目录 → 构建 13.6s → 启动 4.5s；顺带抓到一个真缺陷 —— 未匹配路径返回 **500 而非 404**，已修并用真启动复验；G1 第三条（ArchUnit 拦故意跨层）经查**早已满足**，未重做 | `docs/evidence/v0.1/g1-boot-check.md`、`g1-jar-boot.log`、`scripts/boot-check.sh`、`GlobalExceptionHandlerTest` |
| 2026-11-04 | CAS-03 | 台账导入**真跑通过**（此前只有静态对账顶替）：226 行 preview 零错误 → commit 入库 226 → 重导 226 行全报「编号已存在」；`CaseImportIT` 入 `-Pit` 套件 | `docs/evidence/v0.2/cas03-import-it.log`、`backend/src/test/java/com/qa/autotest/application/caseasset/CaseImportIT.java` |
| 2026-11-04 | ENV-01~05 | 五项全转 `待验`，环境类阻断清零：口令重置、`robot_it`→V7_1/44 表、表头对账 1/14→**6/14 PASS**；顺带修掉三个「验证在说谎」（`-Pit` 空跑、`ArtifactVerifier` 假绿、`check_import_header` 假红）；全量 **450 单测全绿** + `SchemaIT` **5/5** | `docs/evidence/v0.1/{env02-build-test-20261104.log, env04-header-check.log, env-README.md}`、`docs/plan/决策纪要-20261104.md` §4 |
| 2026-11-04 | 计划 | 规划升 **v2.1**：新增 ENV-01~05、状态扩五态、新增 §16.1 墙上时钟门；本表同步为 61 包 | `docs/自动化测试平台中长期工作规划.md` §6.1/§6.4/§16.1、`docs/plan/决策纪要-20261104.md` |
| 2026-11-03 | 评估 | 开发计划评估：56/56 待验、七版齐发导致门禁失效、环境欠账 9 包；复跑后端 `Tests run: 444` 全绿 | `docs/plan/开发计划评估与后续推进-20261103.md` |
| 2026-11-02 | EDT-03 | 三视图切换 3 轮往返 sha256 不变（纯函数 + 组件两层断言 2/2 绿）；顺带修掉序列化把 `FOR/IF/TRY` 嵌套块压平的问题（`RobotStep.depth`） | `docs/evidence/v0.3/vitest-three-views.log`、`packages/editor/tests/three-views.spec.ts` |
| 2026-11-02 | EDT-02 | 编辑器 props 收敛为 `content` / `readonly` / `height`，仓库与路径改由 `setEditorScope()` 注入，新增 `dirty` 事件；全包 0 HTTP / 0 `repoId` | `docs/evidence/v0.3/edt02-scan.log`、`npm-build-edt02.log` |
| 2026-11-02 | EDT-01 | 编辑器依赖闭包抽到 `packages/editor`（40 个上游文件 + 9 个适配缝），独立构建 82 modules / gzip 81 kB，包内 HTTP 0 命中 | `packages/editor/UPSTREAM.md`、`docs/evidence/v0.3/npm-build-editor.log`、`http-scan.log` |
| 2026-11-02 | IAM+AST+CAS | v0.2 后端 36 用例全绿（鉴权/租户/权限矩阵/凭据/文件路径）、前端 build+lint 双 0 | `docs/evidence/v0.2/mvn-test.log`、`npm-build.log`、`npm-lint.log` |
| 2026-11-02 | IAM-02/03 + AST-01~04 + CAS-02/03 | 前端登录态、路由守卫、项目选择器、资产/用例/管理三页接入真实 API | `frontend/src/stores/auth.ts`、`views/{Login,Assets,Cases,Admin}View.vue` |
| 2026-11-02 | DOC-02 / DOC-03 | 后端与前端约定落盘；6 菜单 IA 冻结纪要（editor 一份留 v0.3） | `docs/CONVENTIONS-backend.md`、`docs/CONVENTIONS-frontend.md`、`docs/release/v0.2-ledger/DOC-03-IA冻结纪要.md` |
| 2026-11-02 | WS-02 | Spring Boot 分层骨架 + ArchUnit R1~R5/无环 + 统一响应体；`mvn -B test` 13 用例全绿 | `docs/evidence/v0.1/mvn-test.log` |
| 2026-11-02 | WS-03 | Vue 3 骨架：6 路由 + Pinia + i18n + fetch 封装；`npm run build` / `lint` 均 exit 0 | `docs/evidence/v0.1/npm-build.log`、`npm-lint.log` |
| 2026-11-02 | WS-04 | schema v1：38 表 7 域 + 手写 down + 4 个 db 脚本（**未实跑**） | `db/migration/V1__baseline.sql`、`db/README.md` |
| 2026-11-02 | WS-05 | CI 三关 workflow + runner 安装脚本 + 本地三关降级脚本（**runner 未注册**） | `.github/workflows/pr.yml`、`scripts/ci-local.sh`、`scripts/runner-install.sh` |
| 2026-11-02 | WS-06 | 环境基线文档 + `make env-check` 七组自检 | `docs/env/环境基线.md`、`scripts/env-check.sh` |
| 2026-11-02 | DOC-04 | ADR 机制：索引 + 模板 + 0001~0004 | `docs/adr/README.md` |

### 2026-11-04 20:45 补记（F1-1 后续，大师授权）

1. **ENV-01~05 状态落表**：F1-1 大师裁决五包通过；20:27「都按你的建议 继续推进开发」授权学徒按裁决代改，
   本次把 5 行由 `待验` 改为 `已完成`。这是项目纪律「`待验 → 已完成` 只能由人改」的**一次有留痕的破例**，
   授权来源与证据同时记在 `docs/evidence/v0.1/G1-核验纪要-20261104.md` 修订记录，不是 AI 自批。
2. **#78 / ENV-06**：G1 #4 回滚复现时挖出三处脚本缺陷，当场修好并复跑通过，按纪律仍留 `待验`。
3. **#70 / PRJ-03**：引用保护实现 + `AssetScopeIT` 真库验收通过（1/1），回归 171/171，转 `待验`。
4. 汇总行同步为 78 包 = 6 未开始 / 59 待验 / 8 待现场 / 5 已完成。

### 2026-11-04 22:15 补记（大师「继续开发 不要自动停止」授权）

1. **#77 / EXE-09**：生成侧改为「实例前缀 + 同秒单调序列」，测试改确定性断言（7/7，连跑 10 次零 flaky），转 `待验`。
   **口径修订**：`uk_run_run_id` 唯一索引与 `RunService` 冲突回读本来就有，F1-1 纪要「缺硬唯一保障、会串数据」
   一句被源码推翻，已在 G1 纪要 §3 留修订记录，不悄悄改字。
2. **#78 / ENV-06**：新增 `DownScriptGuardTest` 5/5 静态守门，把三处回滚缺陷固化（含「注释里提到禁用语法不算违规」），
   双向破坏性验证通过；状态仍 `待验`，由人裁决。
3. **#71 / UX-02b**：通用批量端点 + 两页接入，真库 IT 3/3、前端 21/21、全量单测 476 / IT 25 全绿，转 `待验`。
   **偏差**：报告页未接入（`runId` 字符串键 + 导出任务型操作与 `ids: number[]` 契约不兼容），
   已写进 `TEST-PLAN` §13.12 与本行备注；G2 不得据此判「已完整验收」。
4. **#73 / EXEC-01**：大师 22:01 裁决**完全冻结到 F2 现场**，不开发、不空跑、不落派发消费者。
5. **#74 / UX-04**：试点评审闸门未过，本轮只出准备件，未装组件库、未改视图/样式。
6. 汇总行同步为 78 包 = **4 未开始 / 61 待验 / 8 待现场 / 5 已完成**；`待验 → 已完成` 本轮一个都没改。

### 2026-11-04 23:00 补记（大师「参考 PRD / 开发计划 / 测试计划 继续开发」）

1. **#79 / WS-06**：按七版 DEV-PLAN「完成判定」逐项实跑 19 项验收，首次 4 红（编辑器测试环境 /
   颜色守门 / 导入表头依赖 / 闭包脚本调用），修复后全绿。**最重要发现**：`lint.sh` 从未调用六个
   `check_*.py` 守门 —— 写了守门却没接线，之前「CI 三绿」是假绿。已接线，证据
   `docs/evidence/acceptance-20261104/`。状态 `待验`。
2. **#74 / UX-04**：纠正准备清单 v1.0 的逻辑倒置，交付 §2.2 步骤 0 两页试点（Naive UI /
   Element Plus 各一版）+ 对比报告 + gzip 体积实测；状态 `未开始 → 进行中`。
   **步骤 1~8 仍冻结**，评审结论归大师；主仓 `frontend/` 零改动。
3. 汇总同步为 **79 包 = 3 未开始 / 1 进行中 / 62 待验 / 8 待现场 / 5 已完成**；
   `待验 → 已完成` 本轮一个都没改。
4. **七版流程 ①/②/⑥ 欠账本轮未补**：机检 7 版仍 `IDLE`。原因是补齐 ①/② 会激活判据 3
   （每条 TC 要有结果），而 413 条 TC 尚无逐条实测台账，先补文档会「越补越红」；且事后补 ①/②
   不能追溯洗白既有 PRD（流程 §9 明写「无 ② 就开 PRD → PRD 作废重审」）。
   **留待大师裁决后单开一轮**，不在本轮以补文档制造假进展。

### 2026-11-05 00:20 补记（大师「不用评审 先可以开工 优先推进程序功能」）

1. **#74 / UX-04 程序骨架落地**：评审闸门由大师明确免除，`进行中 → 待验`。主仓首次引入
   `naive-ui@2.45.3` / `lucide-vue-next` / 打包字体子集，落六预设强调色 12 套运行时主题 +
   三层持久化 + 首帧防闪、库主题单向派生、图标单一出口、五种自建组件、13 处 `window.confirm` 替换、
   仅 DEV 的 `/dev/styleguide`。证据 `docs/evidence/v0.3/ux04-s1-20261104/`（19 份日志 rc 全 0）。
2. **新增 #80 / UX-04b（视图组件化迁移）**：S1 只到「骨架 + 局部替换」，13 个业务视图仍用原生元素。
   拆成独立包，避免 `UX-04` 因「永远差最后几页」卡在 `进行中`。**已开工（首批 5 处）**：
   `PlansView` 计划状态徽标 2 处 → `StatusTag`（并用 `warn` 语气区分「已暂停」）、
   `CasesView` / `AuditView` / `AdminView` 主列表空态 3 处 → `EmptyState`，同时删掉变成死代码的
   `.badge*` 样式。状态 `进行中`。
3. **本轮抓到的真缺陷（不是风格问题）**：后端偏好白名单 `ALLOWED` 只有 `theme` / `locale`，
   没有 `accent` → 前端每次切强调色 `PUT {accent}` 都被 400 拒，而前端按「偏好同步失败不回滚」
   静默吞掉，表现为「本机有效、换设备丢失」，**日志里什么都没有**。已补 `accent` 六值白名单 +
   `UserPreferenceIT` 增「六预设可存取」「非法值被拒且不落库」两条，集成测试 27/27。
   教训：偏好类能力「看起来工作了」不算数，必须有一条真库 IT 走完 写入→读回→换用户隔离。
4. **第二个真缺陷：图标名拼错是静默失败**。`EmptyState` 用了 `icon="shield"`，表里只有 `audit`；
   项目未开 `vueCompilerOptions.strictTemplates`，模板 prop 不被 `vue-tsc` 检查，`build` 全绿，
   运行时渲染空白。修法：改名 + `AppIcon` DEV 警告与 `help` 兜底 + 新增 `tools/check_icons.py`
   静态对账并接进 `lint.sh`（自检覆盖「未定义名」「表被清空」）。
5. 汇总同步为 **80 包 = 3 未开始 / 1 进行中 / 63 待验 / 8 待现场 / 5 已完成**；
   `待验 → 已完成` 本轮仍然一个都没改。
6. **F1/F2 边界**：`EXEC-01` 在 11-04 22:01 裁决下完全冻结，八条 `待现场` 包一条未碰；该冻结已在 11-05 由大师解冻（见下条补记）。

### 2026-11-05 13:20 补记（大师「继续推动开发」→ EXEC-01 解冻实现）

1. **#73 / EXEC-01 解冻**：大师在评估后选择本包开工，原「冻结到 F2 现场」裁决作废。
   状态 `未开始 → 待验`（AI 不改 `已完成`）。
2. **派发器公共化**：`RunDispatcher` 从计划域迁到 `application/execution`，
   `PlanRunDispatcher → SerialRunDispatcher`（单线程串行 + runId 在途去重 + `TenantContext.runAs`）；
   无调用方的 `RunQueuePolicy` 及其测试删除（评估书允许的处置项）。
3. **人工触发**：`QuickRunService` + `POST /api/execution/quick-run`（`EXECUTION:EXECUTE`、
   ≥1 套件、路径规范化、落库 `manual` 后立即派发、`RUN_TRIGGER` 审计含 source/suites/operator）；
   `POST /api/execution/runs` 改为 **CI 专用**并接同一派发器，僵尸 queued 不再出现。
4. **用例集立即执行**：`POST /api/execution/test-sets/{id}/run`，按 `case_code == 脚本测试名`
   扫描脚本工作区解析套件；未绑定整单 400 并列出编号；空集合 400、集合不存在 404。
5. **前端**：`RunsView` 新增「手动执行」内联表单（套件路径 / 用例集两种来源、环境/并发/超时/标签），
   提交后刷新列表并直接接上该 run 的实时日志；i18n 中英双份，`check_i18n` 通过。
6. **门禁实测**：后端 `Tests run: 488, Failures: 0`（`-Pit` 28/28）；前端 `153 passed`；`vue-tsc` / `npm run build` /
   `bash scripts/lint.sh` 均退出 0。证据 `docs/evidence/v0.2/exec01/`。
7. 汇总同步为 **82 包 = 1 未开始 / 0 进行中 / 68 待验 / 0 待现场 / 13 已完成**；
   `待验 → 已完成` 本轮仍然一个都没改。

### 2026-11-05 14:10 补记（EXEC-02 提前实现，v0.4 候选、不计入 v0.2 包数）

大师 11-05 追加指示「继续做 EXEC-02」。EXEC-02 是 **v0.4 候选**（勾选台账用例直接执行），
不在 v0.2 的 82 包里，因此**不改上方汇总、不占包号**，按包外提前实现留痕：

| 项 | 结果 |
|---|---|
| 状态 | 包外 · 待验（v0.4 立项时转为正式包） |
| 绑定模型 | ADR-0005 §3：脚本测试 `[Tags] case:<case_code>` 优先；测试名 == 编号兜底；任一条未绑定整单 400 列编号 |
| 后端 | `CaseScriptResolver`（新）、`CaseRunService`（新）、`POST /api/execution/cases/run`；`--test` 透传（`CreateRunCommand`/`ShardSpec` 增 `testNames`）；`TestSetRunService` 改为复用同一解析器 |
| 前端 | `executionApi.runCases` + CasesView 批量条「执行所选」内联表单（环境/并发/超时/标签）+ i18n 中英 |
| 门禁 | 后端 **496** / 集成 **28** / 前端 **155** / `vue-tsc`+`build` 退出 0 / `lint.sh` **PASS** |
| 证据 | `docs/evidence/v0.4/exec02/`（README + 6 份日志） |
| 偏差 | ① `--test` 未实跑（ADR-0013 禁跑令）② 绑定读 DB、执行器读磁盘，两工作区可能不一致 ③ 用例集执行仍为套件粒度 |
| 未提交 | 本轮无任何 git 提交，改动留在工作区等大师复核 |

> 2026-11-05 14:12 大师指示「把 EXEC-02 正式并入 v0.4 包表」，本条已升为 **WBS #83**（见下条补记），原「不改汇总」口径作废。

### 2026-11-05 14:12 补记（大师「把 EXEC-02 正式并入 v0.4 包表」）

1. **#83 / EXEC-02 入表**：不再按包外候选留痕，正式登记为 **v0.4 执行版补充包**；
   包表同步改在 `docs/release/v0.4-execution/DEV-PLAN.md` §1.3 与 `spec.md` §8。
2. 状态 **`待验`**（实现与门禁已在 14:10 完成，AI 不改 `已完成`）。
3. 汇总同步为 **83 包 = 1 未开始 / 0 进行中 / 69 待验 / 0 待现场 / 13 已完成**；
   自洽由 `tools/check_wbs_consistency.py` 机检。
4. 证据 `docs/evidence/v0.4/exec02/`；三条偏差不变（`--test` 未实跑 / 绑定读 DB 与执行读磁盘 / 用例集仍套件粒度）。

### 2026-11-05 14:28 补记（大师「继续做 EXEC-03」）

1. **#84 / EXEC-03 入表**：用例集执行由套件粒度改为**测试粒度**，与 EXEC-02 的 `--test` 链路统一；
   包表同步改在 `docs/release/v0.4-execution/DEV-PLAN.md` §1.3 与 `spec.md` §8。
2. 状态 **`待验`**（门禁：后端 498 / 集成 28 / `lint.sh` PASS，AI 不改 `已完成`）。
3. 汇总同步为 **84 包 = 1 未开始 / 0 进行中 / 70 待验 / 0 待现场 / 13 已完成**。
4. 证据 `docs/evidence/v0.4/exec03/`；残余：`--test` 未实跑、同名测试全局匹配、空编号用例静默跳过、
   绑定读 DB 与执行读磁盘。

### 2026-11-05 14:36 补记（大师「继续做 EXEC-04」）

1. **#85 / EXEC-04 入表**：执行前预检前移到请求期 —— 套件路径存在性、用例编号非空、测试名跨套件重名，
   命中直接 400，不落库、不排队；包表同步改在 `docs/release/v0.4-execution/DEV-PLAN.md` §1.3 与 `spec.md` §8。
2. 状态 **`待验`**（门禁：后端 506 / 集成 28 / `lint.sh` PASS，AI 不改 `已完成`）。
3. 汇总同步为 **85 包 = 1 未开始 / 0 进行中 / 71 待验 / 0 待现场 / 13 已完成**。
4. 证据 `docs/evidence/v0.4/exec04/`；残余：工作区需先同步脚本、目录路径不查重名、
   `--test` 未实跑、绑定读 DB 与执行读磁盘。

### 2026-11-05 15:10 补记（大师「结合 PRD 与总体开发计划，连续推进」）

1. **近期计划**：v0.4 收口 EXEC-05（工作区落盘）→ EXEC-06（全量日志下载）→ EXEC-07（租约强制释放）
   → EXEC-08（34 条 FR 覆盖复核）；随后按复核 gap 补 v0.5 报告（已知 RPT-03 手动基线 UI）；
   v1.0 `UX-06` 需现场截图，暂不启动。
2. **#86 / EXEC-05 入表**：ADR-0005 的 `robot_file.content` 在执行期真正成为唯一真源 ——
   每个 run 把项目脚本落盘到自己的 `workspace/`，pabot 以它为 cwd；请求期预检同步改 DB 优先、
   磁盘回落，关闭 EXEC-03/04 留下的「绑定读 DB、执行读磁盘」缺口。
3. 状态 **`待验`**（门禁：后端 510 / 集成 28 / `lint.sh` PASS，AI 不改 `已完成`）。
4. 汇总同步为 **86 包 = 1 未开始 / 0 进行中 / 72 待验 / 0 待现场 / 13 已完成**。
5. 证据 `docs/evidence/v0.4/exec05/`；残余：数据库为空时回落磁盘、
   落盘工作区真实 pabot 执行待现场确认（ADR-0013）。

### 2026-11-05 15:50 补记（近期计划 A2）

1. **#87 / EXEC-06 入表**：全量日志下载落地 —— `RunLogFileService` 统一定位 `<runDir>/run.log`，
   `GET /api/execution/runs/{runId}/log/download` 以附件返回；运行页日志面板加「下载完整日志」。
   实时频道（WebSocket / `recent` 接口）仍只保留最近 2000 行，完整日志不再依赖内存缓冲。
2. 状态 **`待验`**（门禁：后端 514 / 集成 28 / 前端 156 / `vue-tsc`+`build` 0 / `lint.sh` PASS）。
3. 汇总同步为 **87 包 = 1 未开始 / 0 进行中 / 73 待验 / 0 待现场 / 13 已完成**。
4. 证据 `docs/evidence/v0.4/exec06/`；残余：`run.log` 不存在 → 404、产物清理后不可下载、
   下载按钮仅在日志面板打开时可见。

### 2026-11-05 16:10 补记（近期计划 A3）

1. **#88 / EXEC-07 入表**：租约可视化与管理员强制释放 —— 资产页新增「租约」只读页签
   （资源键 / 占用运行 / 剩余时间 / 状态），搜索框与「新建」在该页隐藏；「强制释放」按钮仅管理员渲染，
   二次确认文案念出占用者 `runId` 与资源键，`confirm` 参数直接取该租约的 `runId` 与后端口径一致。
2. 后端 `LeaseAdminService`：非管理员 403、无生效租约 404、`confirm` 不匹配 400；
   成功路径把仍 `RUNNING` 的原运行置为 `FAILED`（`failureClass = failureReason = lease_revoked`），
   再走 `LeaseService.releaseRun`（沿用既有 `@Audited(lease:force-release)`）。
3. 状态 **`待验`**（门禁：后端 521 / 集成 28 / 前端 160 / `vue-tsc`+`build` 0 / `lint.sh` PASS）。
4. 汇总同步为 **88 包 = 1 未开始 / 0 进行中 / 74 待验 / 0 待现场 / 13 已完成**。
5. 证据 `docs/evidence/v0.4/exec07/`；残余：强制释放不终止子进程（只摘租约并标记失败）、
   租约表只列生效中的租约、非管理员无任何释放入口。

### 2026-11-05 16:40 补记（近期计划 A4）

1. **#89 / EXEC-08 入表**：v0.4 PRD 34 条 FR 逐条覆盖复核。结论 **20 覆盖 / 12 部分 / 0 缺失 / 2 待现场**，
   逐条给出实现位置与测试证据，详见 `docs/release/v0.4-execution/FR-COVERAGE-20261105.md`。
2. 最关键的发现：**租约组件已实现但未接入执行链路** —— 全仓 `LeaseService.acquireAll` 只被单测调用，
   `RunExecutionService` 仍是「W2 最小闭环（无租约服务）」；因此 FR-04 / 07 / 08 / 09 / 10 / 23 全部降为「部分」，
   租约页在真实使用中恒空、强制释放恒 404。这是 v0.4「正确性地基」的总闸，建议下一包 **EXEC-09**。
3. 其余 P0：`SerialRunDispatcher` 全局单线程 + 入队未按 priority（FR-03 / 05 与实现不符）；
   P1：设备范围字段缺失（FR-01）、单用例超时缺失（FR-24）、重试次数未展示（FR-29）、日志过滤只有一档（FR-20）。
4. 状态 **`待验`**；复核包不改实现，**不计入 v0.4 交付包数**（`release/README.md` 仍为 15）。
5. 汇总同步为 **89 包 = 1 未开始 / 0 进行中 / 75 待验 / 0 待现场 / 13 已完成**。

### 2026-11-05 17:20 补记（近期计划 A5 / EXEC-08 缺口首位）

1. **#90 / EXEC-09 入表**：租约接入执行链路 —— `RunExecutionService` 在进入 `RUNNING` 前按 run 维度
   `acquireAll`，成功后状态机才允许跃迁；`lease_timeout` 直接落 `FAILED(INFRA)` 且不启动 pabot；
   执行段用 `try/finally` 保证成功 / 失败 / 异常 / 并发取消都 `releaseRun`。
2. 资源键口径：请求带 `deviceKeys` 时用「设备+端口」键（去重、≤128 字符），未声明设备范围时用
   `run:<runId>` 自持（不与其他 run 互斥）；`execution_job` 与 run 同事务创建，`findJobId` 按
   `job_no = run_id` 反查租约外键。
3. 状态 **`待验`**（门禁：后端 527 / 集成 28 / 前端 160 / `vue-tsc`+`build` 0 / `lint.sh` PASS）。
4. 汇总同步为 **90 包 = 1 未开始 / 0 进行中 / 76 待验 / 0 待现场 / 13 已完成**；v0.4 交付包数 15→16。
5. 证据 `docs/evidence/v0.4/exec09/`；残余：租约按 run 聚合（不按分片拆 job）、FR-08 等待态 UI 仍缺、
   `lease_wait_ms` 未回写运行表。

### 2026-11-05 17:55 补记（近期计划 A6 / 队列语义 P0）

1. **#91 / EXEC-10 入表**：`SerialRunDispatcher`（全局单线程）→ `ProjectRunDispatcher`
   （按项目分道，单项目并发 = `execution.max-parallel`，默认 4），队列元素实现 `Comparable`：
   priority 降序 → 提交序号升序；`PriorityBlockingQueue` 无界，达上限只排队不拒绝。
2. 跨项目互不阻塞：每项目一条独立 `ThreadPoolExecutor`（核心=上限，60s 回收空闲线程）；
   runId 在途去重仍全局生效；提交仍用 `TenantContext.runAs` 包裹。
3. `RunDispatcher.submit` 增加 `priority` 参数；`QuickRunService` / `PlanScheduleService` /
   `PlanScheduler` 三个提交点统一取 `command.priority()`（0~9，越大越先）。
4. 状态 **`待验`**（后端 529 / 集成 28 / 前端 160 / `vue-tsc`+`build` 0 / `lint.sh` PASS）。
5. 汇总同步为 **91 包 = 1 未开始 / 0 进行中 / 77 待验 / 0 待现场 / 13 已完成**；v0.4 交付包数 16→17。
6. 证据 `docs/evidence/v0.4/exec10/`；残余：`execution_job.queue_state` 与排队位次接口未做。

### 2026-11-05 18:00 补记（近期计划 A7 / EXEC-11 设备范围）

1. **#92 / EXEC-11 入表**：`run.device_scope`（`V7_4__run_device_scope.sql` + down）落库；
   `RunService` 把请求 `deviceKeys` 去重保序拼成逗号串（≤512 字符，空/全空白记 NULL），
   `RunRecord` / `RunRepositoryJdbc` / 前端 `ExecutionRun` 全链路回溯。
2. 前端：手动执行表单新增「设备范围」多选（设备名下拉 + `tag` 手填端口键，如 `DUT-01/mlx5_0`），
   quick-run / 用例集立即执行都带 `deviceKeys`；日志面板显示该运行的设备范围，未声明时写「全部设备（不互斥）」。
3. **迁移版本号取 V7_4 而非 V4_4**：V4_1~V4_3 与 V5/V6/V7 系列已在开发库执行，补插低位版本会触发
   Flyway out-of-order 拒绝；语义归属仍是 v0.4 执行包。
4. 状态 **`待验`**（后端 533 / 集成 28 / 前端 162 / `vue-tsc`+`build` 0 / `lint.sh` PASS）。
5. 汇总同步为 **92 包 = 1 未开始 / 0 进行中 / 78 待验 / 0 待现场 / 13 已完成**；v0.4 交付包数 17→18。
6. 证据 `docs/evidence/v0.4/exec11/`；残余：并发度 1/2/4 三档未做、列表页未加设备范围列。

### 2026-11-05 19:30 补记（近期计划 P1 / EXEC-12 单用例超时）

1. **能力缺口确认**：Robot Framework 7.5.1 实测 `robot --help` **没有任何 timeout 选项**，
   单用例超时只能靠套件级 `*** Settings ***` 的 `Test Timeout`。
2. **#93 / EXEC-12 入表**：新增 `execution.test-timeout-seconds`（`ExecutionProperties`，默认 `1800`，
   `<=0` 关闭）；`TestTimeoutInjector` 在 materialize 成功后向工作区副本的 `.robot` 套件注入
   `Test Timeout    <N>s`，已有 `Test Timeout` 的套件保持用户原值；`Test Timeout` 命中后
   `ArtifactVerifier.testTimedOut` 从 `output.xml` 的 `msg` 识别，`PabotOrchestrator` 把 run 原因记为
   `test_timeout`（`FailureClassifier` 按 `timeout` 归 CANCELED，符合 PRD「超时按取消处理」）。
3. **不碰用户原脚本**：注入只在 `workspaces.materialize` 返回副本时执行；回落到真实 workspace-root 时跳过。
4. 状态 **`待验`**（后端 539 / 集成 28 / 前端 162 / `vue-tsc`+`build` 0 / `lint.sh` PASS）。
5. 汇总同步为 **93 包 = 1 未开始 / 0 进行中 / 79 待验 / 0 待现场 / 13 已完成**；v0.4 交付包数 18→19。
6. 证据 `docs/evidence/v0.4/exec12/`；残余：`.resource` 不注入、按用例单独配置与 UI 可调未做。

### 2026-11-05 17:15 补记（近期计划 P1 / EXEC-13 重试可追溯）

1. **#94 / EXEC-13 入表**：`attemptNo` 早已由后端 `RunRecord` → `RunView` → 前端 `ExecutionRun` 全链路回溯，
   缺的只是展示。日志面板新增「第 N 次尝试」标记（`execution.attemptLine`），`attemptNo > 1` 才渲染，
   首次尝试不出现，避免正常记录被同一句提示占满。
2. **不碰后端**：本包只改前端（`RunsView.vue` + 两套 i18n + 视图测试）；`retry_of_run_id` 未对外暴露，
   「上一次失败原因」需要新增 API 字段，已在残余里登记。
3. 状态 **`待验`**（后端 539 / 集成 28 / 前端 163 / `vue-tsc`+`build` 0 / `lint.sh` PASS）。
4. 汇总同步为 **94 包 = 1 未开始 / 0 进行中 / 80 待验 / 0 待现场 / 13 已完成**；v0.4 交付包数 19→20。
5. 证据 `docs/evidence/v0.4/exec13/`；残余：`retry_of_run_id` 未对外暴露，看不到「上一次失败原因」。

### 2026-11-05 17:25 补记（近期计划 P1 / EXEC-14 日志级别过滤）

1. **#95 / EXEC-14 入表**：`FR-COVERAGE` 里 FR-20 的最后一条缺口是「过滤只有『只看失败』一档」。
   现在换成 ERROR / WARN / INFO 三个独立勾选（默认全开），着色沿用既有 `level-warn` / `level-error`。
2. **三档全关的处理**：允许全关，但空态文案改成「当前级别过滤下没有日志」——不能让用户以为这份 run 没日志。
   未知级别按 INFO 放行，宁可多显示一行，也不要把没见过的 level 藏掉。
3. **DEBUG 档不做**：后端 `RunLogService` 只产出 ERROR / WARN / INFO（FAIL 已归 ERROR），
   没有数据源的档位做出来就是空勾选框，登记为残余、留给 v0.5 日志补口。
4. 状态 **`待验`**（后端 539 / 集成 28 / 前端 164 / `vue-tsc`+`build` 0 / `lint.sh` PASS）。
5. 汇总同步为 **95 包 = 1 未开始 / 0 进行中 / 81 待验 / 0 待现场 / 13 已完成**；v0.4 交付包数 20→21。

### 2026-11-05 17:40 补记（v0.5 / RPT-03 手动基线 UI 补口）

1. **补的是 RPT-03 的阻塞项**：v0.4 的 P0/P1 清空后转入 v0.5，RPT-01~07 虽已全部 `待验`，
   但 RPT-03 的阻塞列一直挂着「手动基线选择未做前端 UI（接口已支持）」。本包把这条补齐，
   **不新增 WBS 包号**（仍是 #40 / RPT-03），包数与五个计数都不变。
2. **新增候选接口**：`GET /api/reports/{runId}/baseline-candidates` —— 同项目 + 同 DUT/LINK +
   finished + 排除本轮 + 按 `started_at` 倒序，上限 20。口径与 `resolveBaseline` 的校验一致：
   能出现在候选里的，选了一定不会被后端 400 打回。
3. **前端**：详情页「本轮 vs 基线」头部加基线下拉，首项固定「自动（最近一次）」，选中即带
   `baselineRunId` 重新拉对比；选回「自动」时传 `undefined` 而不是字符串 `"undefined"`。
   候选拉取失败只让下拉剩「自动」一项，不把对比本身判红。
4. **真库校验**：新增 `CompareBaselineIT`（1 条，连 `robot_it`），覆盖倒序、NULL 开始时间排最后、
   排除本轮与异 DUT / 异 LINK / 未完成 / 跨项目、`limit` 截断与跨项目 403 ——
   替身仓储怎么写都自洽，只有真库能证明这条 SQL 没多给也没少给。
5. 状态 **`待验`**（后端 **541** / 集成 **29** / 前端 **165** / `vue-tsc`+`build` 0 / `lint.sh` PASS）；
   证据 `docs/evidence/v0.5/rpt03b-baseline/`。残余：真实环境对比仍未跑。

### 2026-11-05 补记（v1.0 / UX-06 双语 · 密度 · 暗色，全表清零）

1. **本表最后一个 `未开始` 包落地**：`UX-06` → `待验`，汇总变为
   **95 包 = 0 未开始 / 0 进行中 / 82 待验 / 0 待现场 / 13 已完成**。此后进度只由验收推动，不由开发推动。
2. **三判据逐条实测**（`docs/evidence/v1.0/ux06/`）：en-US 13 路由 × 1440/1920 共 26 页 `lang=en-US` 且
   `overflowPx=0`；密度 `--table-row-h` 44 ↔ 36 在 `/assets`、`/runs` 首行实测一致、单元格 padding 归零；
   暗色 13 页 WCAG 探针修后 `offenders=0`。
3. **只改有证据的项**：暗色首轮仅 report-detail（3 类）与 report-print（1 类）违规，全部是库默认色与
   固定白纸的冲突，修在 `naiveTheme.ts` 的 Button / Radio 派生层与打印页 `.muted`，未重设任何色板。
   其中 Button 的**禁用态**是第二轮才补的（`textColorDisabledPrimary` 是独立键，首轮改 `textColorPrimary` 不生效）。
4. **走查顺带修掉两个整页级真缺陷，两者此前门禁全绿**：`AuditView` 的 `NDatePicker` 初值 `''` 交
   date-fns 抛 `RangeError` → **整页白屏**（改 `null`）；`locales/index.ts` 顶层浅合并让编辑器包 `editor`
   段整段覆盖平台同名段 → `/editor/1` **直接显示原始键名**（改字段级合并）。各补回归，并暴露一个共性：
   挂载期抛错在 build / eslint / 类型检查里都看不见，只有真挂载才现形。
5. 门禁：前端 **26 文件 / 181 用例**、`vue-tsc --noEmit` 0、后端 **542/542**、`-Pit` 集成 **30/30**、
   `lint.sh` **EXIT=0**（对比度 280 项、字体子集 1019 字 / 177506 B、0 悬空 var、20 处数据网格全走壳）。
6. **残余（写进包内阻塞列，不隐藏）**：暗色只走 1440 一档；溢出判定为 DOM 级，不含 `ellipsis` 截断；
   无真实 DUT（ADR-0013）。本包按纪律留 **`待验`**。

### 2026-11-05 补记（G2-3 → 新包 EXEC-15 执行安全级闸门）

1. **新入表 #96 / `EXEC-15`**（`待验`）：汇总变为
   **96 包 = 0 未开始 / 0 进行中 / 83 待验 / 0 待现场 / 13 已完成**，`13 / 96 ≈ 13.5%`。
   这是 G2 预检认定的「产品缺口」（`docs/evidence/v1.0/g2-precheck/` §3）按路向 1 落地。
2. **为什么这个缺口必须补**：ADR-0013 只写了判定口径（L2 高危 / LX 按 L2 对待），执行入口一行校验都没有；
   台账 226 条无安全级，等于**谁点「执行所选」谁就能把高危用例跑上 DUT**，禁跑令只是纸面约定。
3. **两处入口都拦，且整单拒绝**：`CaseRunService`（执行所选）与 `TestSetRunService`（执行用例集）
   逐条收集 `code(level)`，有一条不合规就整单 403 `SAFETY_LEVEL_BLOCKED`（消息列前 10 条），
   不做「跳过坏用例继续跑」——部分执行会让人误以为整套都跑过了。
4. **必须同时给补判入口，否则产品是死的**：导入台账没有安全级列 → 全部 `LX`（不可执行）。
   新增批量动作 `setSafetyLevel`（非法值 400），前端用例页加安全级列（StatusTag 三色）+ 筛选 + 批量改级，
   否则 226 条全是不可执行数据，只能改库。
5. **前端禁用只是 UX，闸门在后端**：选中项里**可见**的 L2/LX 会让「执行所选」直接 disabled 并写出编号；
   跨页选中的级差前端看不见，仍由后端整单拒绝兜底。要做得准需加「预检」接口，本轮不做（避免多一条双端契约）。
6. 门禁：后端 **549/549**、`-Pit` 集成 **30/30**、前端 **26 文件 / 185 用例**、`vue-tsc`+`build` 0、
   `lint.sh` PASS（字体子集 1021 字 / 177826 B）、`check_release_docs.py` 0 FAIL。
   **踩到一个坑记下来**：IT 首轮 3 条红是 `robot_it` 没迁到 `V7_5`（`Unknown column 'safety_level'`），
   跑 `scripts/db-migrate.sh` 补齐后全绿——**改迁移后必须先迁 dev/it 再跑 `run_it.sh`**。
7. **接口级实测已补（2026-11-05 23:54，本机 dev 后端 :8081 新 jar + `robot_dev`）**：
   `POST /api/execution/cases/run` 对 `L2` 与还原后的 `LX` 均回 **403 `SAFETY_LEVEL_BLOCKED`**
   （消息含 `TN-IMP-226(L2)` / `(LX)`），`L9` 回 400；同一轮把 G2-4 两条接口一并验了（弱口令 2 种 → 400，
   `POST /api/auth/renew` → 200 且 `expiresAt` 前推 8h 窗口）。原始记录
   `docs/evidence/v1.0/g2-3/http-verify-20261105.md`。这批请求在派发前就被拒，**不需要真实 DUT**，
   但它是接口级证据，现场仍需各留一份读数（见第 8 条）。
8. **残余（写进包内阻塞列）**：现场仍需真实触发一次 L2 用例并留存 403 响应；跨页选中不可见；
   台账补判仍需人工逐条。本包按纪律留 **`待验`**。

### 2026-11-06 补记（G2-6 界面走查 + IAM-02 前端定时续 + CAS-03 事务语义）

1. **G2-6 本机界面走查已过**（`#15 / AST-04`，证据 `docs/evidence/v1.0/g2-6/`，含 3 张截图与可复跑脚本
   `prep-domain.py`）：用例页技术域填 `roce` → 表格 10 行、首行 `TN-IMP-226`，清空 → 226 行；
   设备页技术域下拉选项来自 `GET /projects/1/device-domains`（只有 `全部技术域` / `roce`），
   选 `roce` 后下发 `GET /devices?domain=roce` 并回 3 行（`leaf-switch-01` / `gpu-node-02` / `gpu-node-01`）。
2. **走查暴露一个比代码更硬的缺口：台账没有「技术域」列**。226 行样例台账的列是
   测试编号 / 一级分类 / 二级分类 / 测试用例 / 测试类型 / 预设条件 / 测试步骤 / A端命令 / B端命令 /
   交换机配置 / 预期结果 / 执行状态与证据 / 当前环境适配 / 执行顺序 —— 导入器支持「技术域」别名但源表无此列，
   于是 226 条 `domain_tag` 全为空串，**真实数据上「按技术域筛」等价于筛出 0 条**。
   本次为走查用批量动作 `setDomainTag` 临时标了 `roce`×10 / `storage`×5（dev 库，可重标）。
   口径需大师定：给台账加列 / 按「一级分类」规则映射 / 平台内人工批量标注。
3. **G2-4 前端定时续不再是「等 8 小时」的死角**：`SESSION_TTL=8h` + 提前 5 分钟排期，
   真实浏览器观察不到第二次续期，因此新增 `frontend/src/stores/auth.spec.ts`（假时钟 4 条：
   到点前 1ms 不发 / 到点发 1 次且回写 `auth_expires_at` / 8h-5min 后第 2 次 / 失败清空 / 无令牌不排期），
   浏览器侧只补「整页刷新后不跳登录页、localStorage 三项齐全」（即 `loadMe()` 重排的前提）。
   **记一个边界**：若 `renew` 返回值固定不变，重排算出的 `due` 变负会让 0ms 定时器自激——服务端目前每次返回
   「当时 + 8h」不会触发，但改服务端口径时要留意。
4. 门禁：前端 **27 文件 / 189 用例**（原 26 / 185）、`npm run build` 0、`scripts/lint.sh` PASS、
   `check_release_docs.py` 0 FAIL。
5. 汇总不变：**96 包 = 0 / 0 / 83 / 0 / 13**。`#15 AST-04`、`#8 IAM-02` 均留 `待验`（本机证据不能替代现场签字）。
6. 观察项（不影响判定）：首次进 `/cases` 默认项目是 `AST05-B`（0 条用例），用户先看到空表；
   技术域筛选不随刷新/URL 保留（用例页与设备页一致）。
7. **CAS-03 的三项「未覆盖」已补集成测试**（`#18`）：`CaseImportTxIT` 用最小 Spring 上下文
   （`AnnotationConfigApplicationContext` + `@EnableTransactionManagement` + `DataSourceTransactionManager`，
   不起 Boot / Web / Flyway，连真库 `robot_it`）让 `@Transactional` 真正生效，四条用例全绿：
   整批回滚（第 3 行撞编号 → `CONFLICT`，前两行不留）、`viewer` 与非成员两条 403、
   两线程并发导同一批（只一批落库、库里恰好 5 行，连跑 3 次一致）。
   **顺带修一个缺陷**：并发撞唯一键原本抛 `DuplicateKeyException`，会落到兜底 **500**；
   现于 `CaseImportService.commit` 翻译成 `CONFLICT`（409）。证据 `docs/evidence/v1.0/cas03-tx/cas03-tx-it-20261106.md`。
   仍未覆盖：HTTP 层 403 状态码/响应体、多实例并发规模。**本包仍留 `待验`**——卡的是 461 vs 226 条数口径。

## 更新方式

- **AI**：认领时改 `进行中`；自检通过、开 PR 后改 `待验`（附证据路径）；若需人 / 真机 / 授权才能验，改 `待现场` 并在阻塞列写「需谁做什么」；**不得**自行改 `已完成`。
- **人**：合并 PR 后改 `已完成`，并把证据填实（命令或文件路径）。
- **汇总行**：每次改动后同步**五个**计数（未开始 / 进行中 / 待验 / 待现场 / 已完成），别让它过期——它是所有人第一眼看的东西。

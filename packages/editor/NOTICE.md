# NOTICE

本目录下的 `packages/editor` 包含从下列开源项目衍生的代码。

## 衍生来源

| 项 | 内容 |
|---|---|
| 上游项目 | **viadee/roboscope** |
| 上游版本 | **v0.16.1** |
| 上游许可 | **Apache License 2.0**（原文见同目录 `LICENSE-roboscope`，未作修改） |
| 上游地址 | https://github.com/viadee/roboscope |
| 衍生范围 | 前端编辑器组件闭包：`src/components/editor/**`、`src/components/ui/{BaseButton,BaseModal}.vue`、`src/composables/**`、`src/stores/**`、`src/types/**`、`src/utils/**` |

## 修改声明（Apache-2.0 §4(b)）

本项目对上游代码做了以下修改，逐项列明：

1. **HTTP 能力移除**：上游 `src/api/*` 的 8 个模块改成薄适配缝（`src/api/context.ts` 注入式），
   包内不再包含任何 HTTP 调用。
2. **特性开关去宿主耦合**：`src/composables/useFeatureFlags.ts` 移除对本地上游登录 token 与
   401 拦截器的依赖。
3. **错误提取工具措辞中立化**：`src/utils/errors.ts` 注释中去掉上游 HTTP 客户端专有名词，
   行为不变。
4. （后续包）`RobotEditor.vue` / `FlowEditor.vue` 的 props 契约收敛、品牌与中文包清理，
   逐文件在文件头标注 `Modified by Robot-Autotest`。

## 商标

本项目**不使用**上游产品名称、Logo 或视觉标识。上游商标归其权利人所有，
本项目的名称与标识与之无关。

## 分发义务

对外分发本包时须同时保留：`LICENSE-roboscope`（Apache-2.0 原文）、本 `NOTICE.md`、
以及各修改文件头部的衍生声明。不得移除上述文件，也不得以「内部使用」为由跳过。

---

**检查单**（EDT-08，人签）

- [x] `LICENSE-roboscope` 存在且未被修改
- [x] `NOTICE.md` 写明上游版本与衍生关系
- [x] 20 个改写文件逐个带文件头声明（EDT-02 落地；枚举见 `docs/evidence/v0.3/edt08-license-check.log`）
- [x] 品牌残留产品面 0 命中（含 `dist/`）（EDT-07，`docs/evidence/v0.3/edt07-brand-scan.log`）

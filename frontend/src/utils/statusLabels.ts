/**
 * 枚举值的**唯一**展示映射（FR-v0.2-34）。
 *
 * <p>问题：后端返回的是**受控词表**（`draft` / `FINISHED` / `P0` / `schedule`），
 * 界面直接 `{{ c.reviewStatus }}` 就把内部值暴露给用户了。中文界面里出现
 * 裸 `approved`、英文界面里出现裸 `草稿`，都属于"程序不一致"。
 *
 * <p>本文件之前，同一件事在 4 个页面各写了一遍（`OverviewView.stateText`、
 * `RunsView.stateLabel`、`ReportsView.stateLabel`、`PlansView.runStateLabel`），
 * 且大小写处理各不相同（有的 `toLowerCase()` 有的没有）。集中到一处后，
 * 新增一个状态只需要改一个地方。
 *
 * <p>**未知值不吞掉**：语言包里没有对应键时原样返回。宁可让用户看到一个
 * 不认识的英文，也不要显示空白——空白会让"数据没加载"和"状态没翻译"分不清。
 *
 * <p>**`ENUM_GROUPS` 必须是声明式的**：`tools/check_i18n.py` 靠它逐个值核对
 * `enum.<组>.<值>` 在语言包里是否存在。写成 `lookup('reviewStatus', v)` 这种
 * 散落调用时，脚本看不见"这个组该有哪些值"，删错一个键只能等到界面上
 * 冒出裸 `approved` 才发现（本项目真实发生过一次，见 TEST-PLAN §13.3）。
 */
import { i18n } from '@/locales'

/** 五组受控词表。数组同时供下拉框选项使用，避免选项和映射两处漂移。 */
export const REVIEW_STATUSES = ['draft', 'pending', 'approved', 'rejected'] as const
export const RUN_STATUSES = ['queued', 'preparing', 'running', 'finished', 'failed', 'canceled'] as const
export const PRIORITIES = ['P0', 'P1', 'P2', 'P3'] as const
export const TRIGGER_TYPES = ['manual', 'schedule', 'ci'] as const
export const MEMBER_ROLES = ['owner', 'admin', 'pm', 'engineer', 'member', 'viewer'] as const

export type ReviewStatus = (typeof REVIEW_STATUSES)[number]
export type RunStatus = (typeof RUN_STATUSES)[number]
export type Priority = (typeof PRIORITIES)[number]
export type TriggerType = (typeof TRIGGER_TYPES)[number]
export type MemberRole = (typeof MEMBER_ROLES)[number]

/**
 * 组名 → 取值集合。守门脚本读这份声明。
 *
 * <p>组名要与语言包 `enum.<组名>.<值小写>` 对齐；改这里等于改契约。
 */
export const ENUM_GROUPS = {
  reviewStatus: REVIEW_STATUSES,
  runStatus: RUN_STATUSES,
  priority: PRIORITIES,
  triggerType: TRIGGER_TYPES,
  memberRole: MEMBER_ROLES,
} as const

export type EnumGroup = keyof typeof ENUM_GROUPS

function lookup(group: EnumGroup, value: string | null | undefined): string {
  if (!value) return '—'
  // 运行状态在库里是小写、在部分 DTO 里是大写，统一按小写查键。
  const key = `enum.${group}.${value.toLowerCase()}`
  const te = i18n.global.te
  return te(key) ? i18n.global.t(key) : value
}

/** 用例评审状态：draft / pending / approved / rejected */
export function reviewStatusLabel(value: string | null | undefined): string {
  return lookup('reviewStatus', value)
}

/** 运行状态：queued / preparing / running / finished / failed / canceled */
export function runStatusLabel(value: string | null | undefined): string {
  return lookup('runStatus', value)
}

/** 优先级：P0～P3。两套语言同值，属于刻意保留的技术标识，不算漏译。 */
export function priorityLabel(value: string | null | undefined): string {
  return lookup('priority', value)
}

/** 触发方式：manual / schedule / ci */
export function triggerTypeLabel(value: string | null | undefined): string {
  return lookup('triggerType', value)
}

/** 项目内成员角色：owner / admin / pm / engineer / member / viewer */
export function memberRoleLabel(value: string | null | undefined): string {
  return lookup('memberRole', value)
}

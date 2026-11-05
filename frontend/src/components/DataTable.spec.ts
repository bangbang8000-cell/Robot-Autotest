/**
 * `DataTable` 规范壳（UX-05）。壳自己不出现在页面上，所以它的四件事只能靠组件级回归兜住：
 * ① 有数据 → 正常渲染，且 `scroll-x` 由列宽推导（不是视图里写死的魔法数）；
 * ② 加载中且无数据 → 骨架条，条数 = 列数 × 行数，且**不**同时渲染空态；
 * ③ 加载中但已有数据 → 不闪骨架，旧行留着（翻页/刷新时不该整表消失）；
 * ④ 空数据 → `EmptyState`，可被 `#empty` 插槽覆盖。
 */
import { afterEach, describe, expect, it } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import { i18n } from '@/locales'
import DataTable from './DataTable.vue'

interface Row {
  id: number
  name: string
}

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: 'NAME', key: 'name', width: 220 },
]

let app: App | null = null
let host: HTMLElement | null = null

async function mountTable(props: Record<string, unknown>, slotText?: string): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp({
    render: () =>
      h(
        DataTable as never,
        props as never,
        slotText ? { empty: () => h('p', { class: 'custom-empty' }, slotText) } : undefined,
      ),
  })
  app.use(i18n)
  app.mount(root)
  await nextTick()
  return root
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
})

const rows: Row[] = [
  { id: 1, name: 'alpha' },
  { id: 2, name: 'beta' },
]

describe('DataTable 规范壳', () => {
  it('有数据时渲染表头与行', async () => {
    const root = await mountTable({ columns, data: rows, rowKey: (row: Row) => row.id })
    expect(root.textContent).toContain('NAME')
    expect(root.textContent).toContain('alpha')
    expect(root.querySelectorAll('.n-data-table-tbody .n-data-table-tr').length).toBe(2)
  })

  it('scroll-x 由列宽之和推导，视图不再写死 1000', async () => {
    const root = await mountTable({ columns, data: rows })
    const table = root.querySelector<HTMLElement>('.n-data-table-table')
    expect(table?.style.minWidth).toBe('300px')
  })

  it('minScrollX 作为下限，列宽更大时以列宽为准', async () => {
    const root = await mountTable({ columns, data: rows, minScrollX: 900 })
    const table = root.querySelector<HTMLElement>('.n-data-table-table')
    expect(table?.style.minWidth).toBe('900px')
  })

  it('默认给 max-height：长列表表头吸顶的前提', async () => {
    const root = await mountTable({ columns, data: rows })
    const body = root.querySelector<HTMLElement>('.n-data-table-base-table-body')
    expect(body?.style.maxHeight).toBe('calc(100vh - 340px)')
  })

  it('max-height 传 null 时不限制（整页滚动）', async () => {
    const root = await mountTable({ columns, data: rows, maxHeight: null })
    const body = root.querySelector<HTMLElement>('.n-data-table-base-table-body')
    expect(body?.style.maxHeight ?? '').toBe('')
  })

  it('加载中且无数据：画骨架条，不画空态', async () => {
    const root = await mountTable({ columns, data: [], loading: true, skeletonRows: 3 })
    expect(root.querySelectorAll('.data-table__bar').length).toBe(3 * columns.length)
    expect(root.querySelector('.empty-state')).toBeNull()
    expect(root.querySelector('.data-table')?.getAttribute('aria-busy')).toBe('true')
  })

  it('加载中但已有数据：不闪骨架，旧行保留', async () => {
    const root = await mountTable({ columns, data: rows, loading: true })
    expect(root.querySelectorAll('.data-table__bar').length).toBe(0)
    expect(root.textContent).toContain('alpha')
    expect(root.querySelector('.data-table')?.getAttribute('aria-busy')).toBe('true')
  })

  it('空数据渲染 EmptyState，标题可定制', async () => {
    const root = await mountTable({ columns, data: [], emptyTitle: '没有运行记录' })
    expect(root.querySelector('.empty-state')).not.toBeNull()
    expect(root.textContent).toContain('没有运行记录')
  })

  it('空态可被 #empty 插槽覆盖', async () => {
    const root = await mountTable({ columns, data: [] }, '自定义空态')
    expect(root.querySelector('.custom-empty')).not.toBeNull()
    expect(root.querySelector('.empty-state')).toBeNull()
  })

  it('加载失败用 error 插画与「加载失败」，不能显示「暂无数据」', async () => {
    const root = await mountTable({ columns, data: [], error: true })
    expect(root.querySelector('.empty-illustration--error')).not.toBeNull()
    expect(root.textContent).toContain(i18n.global.t('common.loadFailed'))
    expect(root.textContent).not.toContain(i18n.global.t('common.noData'))
  })

  it('搜索滤空用 no-result 与「没有匹配的记录」，且不吃列表专属文案', async () => {
    const root = await mountTable({
      columns,
      data: [],
      emptyIllustration: 'no-result',
      emptyTitle: '还没有计划',
    })
    expect(root.querySelector('.empty-illustration--no-result')).not.toBeNull()
    // 传了专属标题也要被覆盖：搜不到时「还没有计划」是错误信息
    expect(root.textContent).toContain(i18n.global.t('common.noResult'))
    expect(root.textContent).not.toContain('还没有计划')
  })
})

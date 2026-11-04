/**
 * useSelection 单元测试（UX-02，FR-v0.2-35/36 的正式单测）。
 *
 * 覆盖：本页全选边界、半选态、跨页保留、失败项保留重试、清空。
 * 这些是纯组合式函数行为，不依赖 DOM 与后端。
 */
import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useSelection } from './useSelection'

describe('useSelection 基础行为', () => {
  it('初始为空；toggle 添加与移除', () => {
    const pageIds = ref<number[]>([1, 2, 3])
    const s = useSelection(pageIds)
    expect(s.count.value).toBe(0)
    expect(s.isEmpty.value).toBe(true)

    s.toggle(1)
    s.toggle(2)
    expect(s.count.value).toBe(2)
    expect(s.isSelected(1)).toBe(true)

    s.toggle(1)
    expect(s.isSelected(1)).toBe(false)
    expect(s.count.value).toBe(1)
  })
})

describe('本页全选与半选态（TC-BAT-01/02 的组合层判据）', () => {
  it('本页未全选 → togglePage 只选本页；再点一次只清本页', () => {
    const pageIds = ref<number[]>([1, 2, 3])
    const s = useSelection(pageIds)

    s.togglePage()
    expect(s.pageAllSelected.value).toBe(true)
    expect(s.count.value).toBe(3)

    s.togglePage()
    expect(s.pageAllSelected.value).toBe(false)
    expect(s.count.value).toBe(0)
  })

  it('勾 9/10 条 → 半选态（indeterminate），不是全选', () => {
    const pageIds = ref<number[]>(Array.from({ length: 10 }, (_, i) => i + 1))
    const s = useSelection(pageIds)
    for (let i = 1; i <= 9; i++) s.toggle(i)
    expect(s.pageAllSelected.value).toBe(false)
    expect(s.pageIndeterminate.value).toBe(true)

    s.toggle(10)
    expect(s.pageAllSelected.value).toBe(true)
    expect(s.pageIndeterminate.value).toBe(false)
  })

  it('空页不产生全选态', () => {
    const s = useSelection(ref<number[]>([]))
    expect(s.pageAllSelected.value).toBe(false)
    expect(s.pageIndeterminate.value).toBe(false)
  })
})

describe('跨页保留（FR-v0.2-35）', () => {
  it('页 A 勾 2 条 → 翻页（页 B）再勾 2 条 → 计数 4，跨页 2 条', () => {
    const pageIds = ref<number[]>([1, 2, 3])
    const s = useSelection(pageIds)
    s.toggle(1)
    s.toggle(2)

    // 翻页：父级换数据，pageIds 变化，选择集合不清空
    pageIds.value = [4, 5, 6]
    s.toggle(4)
    s.toggle(5)

    expect(s.count.value).toBe(4)
    expect(s.crossPageCount.value).toBe(2)
    expect(s.isSelected(1)).toBe(true)
    expect(s.isSelected(4)).toBe(true)
  })

  it('表头全选只作用本页，不触碰跨页已选项', () => {
    const pageIds = ref<number[]>([1, 2])
    const s = useSelection(pageIds)
    s.toggle(99) // 上一页残留

    pageIds.value = [1, 2]
    s.togglePage()
    expect(s.count.value).toBe(3)
    expect(s.isSelected(99)).toBe(true)

    s.togglePage()
    expect(s.count.value).toBe(1)
    expect(s.isSelected(99)).toBe(true)
  })
})

describe('批量回执后的失败项保留（FR-v0.2-36）', () => {
  it('retain 只保留失败项，重试只作用于它们', () => {
    const pageIds = ref<number[]>([1, 2, 3, 4])
    const s = useSelection(pageIds)
    s.togglePage() // 全选 4 条

    // 模拟批量结果：2 成功 2 失败
    s.retain([3, 4])
    expect(s.count.value).toBe(2)
    expect(s.isSelected(1)).toBe(false)
    expect(s.isSelected(3)).toBe(true)
  })

  it('clear 一键清空（含跨页项）', () => {
    const pageIds = ref<number[]>([1])
    const s = useSelection(pageIds)
    s.toggle(1)
    s.toggle(99)
    s.clear()
    expect(s.isEmpty.value).toBe(true)
    expect(s.count.value).toBe(0)
  })
})

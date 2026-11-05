import { describe, expect, it } from 'vitest'
import { computeScrollX, DEFAULT_COLUMN_WIDTH } from './tableWidth'

describe('computeScrollX', () => {
  it('全部列都有 width 时取和', () => {
    expect(computeScrollX([{ width: 100 }, { width: 220 }, { width: 80 }])).toBe(400)
  })

  it('缺 width 的列按默认宽度计入', () => {
    expect(computeScrollX([{ width: 100 }, {}, { width: 100 }])).toBe(100 + 100 +
      DEFAULT_COLUMN_WIDTH)
  })

  it('列全部没写 width 时按列数估一个总宽，而不是返回 0', () => {
    expect(computeScrollX([{}, {}, {}])).toBe(3 * DEFAULT_COLUMN_WIDTH)
  })

  it('总宽小于 minScrollX 时取 minScrollX（长路径列等特殊表格）', () => {
    expect(computeScrollX([{ width: 100 }], 900)).toBe(900)
  })

  it('总宽大于 minScrollX 时取总宽，不受下限拖累', () => {
    expect(computeScrollX([{ width: 600 }, { width: 600 }], 900)).toBe(1200)
  })

  it('字符串宽度（含单位）也能解析', () => {
    expect(computeScrollX([{ width: '120px' }, { width: '80' }])).toBe(200)
  })

  it('非法宽度按缺省处理，不会算出 NaN', () => {
    expect(computeScrollX([{ width: 'auto' }, { width: 0 }, { width: -5 }])).toBe(
      3 * DEFAULT_COLUMN_WIDTH,
    )
  })

  it('空列返回 undefined（= 不横滚），除非给了下限', () => {
    expect(computeScrollX([])).toBeUndefined()
    expect(computeScrollX([], 800)).toBe(800)
  })
})

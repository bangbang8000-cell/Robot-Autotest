/**
 * UX-05 表格规范（一）：横滚宽度由列声明推导，不在视图里写死魔法数。
 *
 * 迁库（UX-04b）时 12 处表格都写着 `:scroll-x="1000"`。这个数的坏处不是难看，而是
 * **它和列没有关系**：加一列不加数字，那一列会被压成一行一个字；删一列不减数字，
 * 表尾多出一条无意义的横向空白滚到底。列宽是列自己的属性，总宽应当是它的和。
 *
 * 所以视图不再写 scroll-x，只声明每列 width（确实需要的才写），壳负责求和。
 */

/** 列上与本模块相关的字段（只取 DataTable 里对宽度有意义的两个） */
export interface ColumnWidthLike {
  width?: number | string
  key?: string | number
}

/**
 * 没声明 width 的列按这个宽度计入总宽。
 *
 * 库在没有 min-width 时会把剩余宽度平均分给各列，所以这里不需要精确 —— 估一个
 * 「常见列宽」保证总宽不偏小即可。取 120 的依据：现有 19 处表格的定宽列集中在
 * 100~140（状态、时间、操作列），120 落在中位。
 */
export const DEFAULT_COLUMN_WIDTH = 120

function toPx(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) && value > 0 ? value : null
  if (typeof value === 'string') {
    const parsed = Number.parseFloat(value)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null
  }
  return null
}

/**
 * 推导 `NDataTable` 的 `scroll-x`。
 *
 * - 全部列都有 width：取列宽之和；
 * - 有列缺 width：已声明的求和 + 缺失列数 × {@link DEFAULT_COLUMN_WIDTH}；
 * - 结果与 `minScrollX` 取大者（少数表格内容必须比列宽更宽，例如长路径列）；
 * - 一列都没有时返回 `undefined`：`scroll-x` 不设置 = 不横滚，别硬塞一个 0。
 */
export function computeScrollX(
  columns: readonly ColumnWidthLike[],
  minScrollX = 0,
): number | undefined {
  if (columns.length === 0) return minScrollX > 0 ? minScrollX : undefined
  let declared = 0
  let missing = 0
  for (const column of columns) {
    const px = toPx(column.width)
    if (px === null) missing += 1
    else declared += px
  }
  const total = declared + missing * DEFAULT_COLUMN_WIDTH
  const width = Math.max(total, minScrollX)
  return width > 0 ? width : undefined
}

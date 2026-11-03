/**
 * 趋势 SVG 折线的绘图类型（RPT-04）。
 *
 * 只描述「画什么」：x 为时间戳或序号，y 为耗时 / 通过率，deviated 标黄。
 */
export interface TrendChartPoint {
  /** 点击回调的标识：耗时趋势用 runId，通过率用 bucket。 */
  id: string
  label: string
  x: number
  y: number
  deviated?: boolean
}

export interface TrendChartSeries {
  key: string
  label: string
  points: TrendChartPoint[]
}

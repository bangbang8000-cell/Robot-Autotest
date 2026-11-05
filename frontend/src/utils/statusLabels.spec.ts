/**
 * 状态 → 色档的显示契约测试。
 *
 * 为什么值得单独测：这两个函数是「同一个状态在表格、徽标、概览卡片里颜色一致」的
 * 唯一来源。把 `draft` 从 neutral 改成 warn 这类调整不会让任何构建或类型检查失败，
 * 但会让「等我评审的」在列表里被草稿淹没 —— 只能靠断言兜住。
 */
import { describe, expect, it } from 'vitest'
import { reviewStatusTone, runStatusTone } from '@/utils/statusLabels'

describe('runStatusTone', () => {
  it('终态里只有 finished 算成功', () => {
    expect(runStatusTone('finished')).toBe('ok')
    expect(runStatusTone('FAILED')).toBe('fail')
    // canceled 不是失败：用户主动取消不该染成红色
    expect(runStatusTone('canceled')).toBe('neutral')
  })

  it('running 用 info，排队与准备用 warn', () => {
    expect(runStatusTone('running')).toBe('info')
    expect(runStatusTone('queued')).toBe('warn')
    expect(runStatusTone('preparing')).toBe('warn')
  })

  it('大小写无关，未知值与空值降级为 neutral', () => {
    expect(runStatusTone('Finished')).toBe('ok')
    expect(runStatusTone('whatever')).toBe('neutral')
    expect(runStatusTone(null)).toBe('neutral')
    expect(runStatusTone(undefined)).toBe('neutral')
  })
})

describe('reviewStatusTone', () => {
  it('通过为 ok、驳回为 fail、待审为 warn', () => {
    expect(reviewStatusTone('approved')).toBe('ok')
    expect(reviewStatusTone('rejected')).toBe('fail')
    expect(reviewStatusTone('pending')).toBe('warn')
  })

  it('草稿是 neutral 而不是 warn', () => {
    // 草稿是作者没提交，不是「等人处理」；与 pending 同色会让待审项淹没在草稿堆里
    expect(reviewStatusTone('draft')).toBe('neutral')
    expect(reviewStatusTone('draft')).not.toBe(reviewStatusTone('pending'))
  })

  it('未知值与空值降级为 neutral', () => {
    expect(reviewStatusTone('')).toBe('neutral')
    expect(reviewStatusTone(undefined)).toBe('neutral')
  })
})

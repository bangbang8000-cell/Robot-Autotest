import { describe, expect, it } from 'vitest'
import { looksLikeZip } from '@/api/imports'

describe('looksLikeZip', () => {
  it('accepts plain zip names', () => {
    expect(looksLikeZip('samples.zip')).toBe(true)
    expect(looksLikeZip('SAMPLE.ZIP')).toBe(true)
  })

  it('rejects traversal and non-zip names', () => {
    expect(looksLikeZip('../evil.zip')).toBe(false)
    expect(looksLikeZip('samples.tar.gz')).toBe(false)
    expect(looksLikeZip('')).toBe(false)
  })
})

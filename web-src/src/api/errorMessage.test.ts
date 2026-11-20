import { describe, expect, it } from 'vitest'
import { ApiError } from './client'
import { errorMessage } from './errorMessage'

describe('errorMessage', () => {
  it('returns fallback for non-ApiError', () => {
    expect(errorMessage(new Error('x'), 'fallback')).toBe('fallback')
    expect(errorMessage('plain', 'fallback')).toBe('fallback')
  })

  it('prefers JSON error.message from ApiError body', () => {
    const err = new ApiError(400, '{"error":{"message":"Слоти зайняті"}}')
    expect(errorMessage(err, 'fallback')).toBe('Слоти зайняті')
  })

  it('falls back to ApiError.message when body is not JSON', () => {
    const err = new ApiError(500, 'boom')
    expect(errorMessage(err, 'fallback')).toBe('boom')
  })

  it('uses fallback when JSON has no message', () => {
    const err = new ApiError(400, '{"error":{}}')
    Object.defineProperty(err, 'message', { value: '' })
    expect(errorMessage(err, 'fallback')).toBe('fallback')
  })
})

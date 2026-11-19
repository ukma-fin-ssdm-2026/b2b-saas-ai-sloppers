import { ApiError } from './client'

export function errorMessage(err: unknown, fallback: string): string {
  if (!(err instanceof ApiError)) return fallback
  try {
    const parsed = JSON.parse(err.body) as {
      error?: { message?: string }
    }
    if (parsed.error?.message) return parsed.error.message
  } catch {
    // plain text body
  }
  return err.message || fallback
}

import { ApiError } from './client'

function messageFromJson(raw: string): string | null {
  try {
    const parsed = JSON.parse(raw) as { error?: { message?: string } }
    return parsed.error?.message ?? null
  } catch {
    return null
  }
}

export function errorMessage(err: unknown, fallback: string): string {
  if (!(err instanceof ApiError)) return fallback
  return (
    messageFromJson(err.body) ||
    messageFromJson(err.message) ||
    err.message ||
    fallback
  )
}

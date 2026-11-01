import { ApiError } from '../api/client'

type ApiErrorBody = {
  error?: {
    message?: string
    code?: string
    details?: Array<{ field?: string; message?: string }>
  }
}

/** Human-readable message from backend ErrorResponse or plain text. */
export function apiErrorMessage(err: unknown, fallback: string): string {
  if (!(err instanceof ApiError)) {
    return fallback
  }

  try {
    const parsed = JSON.parse(err.body) as ApiErrorBody
    const details = parsed.error?.details
      ?.map((d) => (d.field ? `${d.field}: ${d.message}` : d.message))
      .filter(Boolean)
      .join('; ')
    if (details) {
      return `${parsed.error?.message ?? fallback} (${details})`
    }
    if (parsed.error?.message) {
      return parsed.error.message
    }
  } catch {
    // plain text body
  }

  if (err.message && !err.message.startsWith('{')) {
    return err.message
  }

  return `${fallback} (${err.status})`
}

/** Simple slug suggestion for admin forms (Latin + digits). */
export function suggestSlug(name: string): string {
  return name
    .trim()
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 50)
}

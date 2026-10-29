import { readSession } from '../auth/session'

export const API_BASE = '/api/v1'

export class ApiError extends Error {
  status: number
  body: string

  constructor(status: number, message: string, body = '') {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

export type ApiRequestOptions = Omit<RequestInit, 'headers'> & {
  headers?: HeadersInit
  /** Explicit token; otherwise session token is used when present */
  token?: string | null
  skipAuth?: boolean
}

export async function apiFetch<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { token, skipAuth, headers: initHeaders, ...init } = options

  const headers = new Headers(initHeaders)
  const resolvedToken =
    token !== undefined ? token : skipAuth ? null : (readSession()?.token ?? null)
  if (resolvedToken) {
    headers.set('Authorization', `Bearer ${resolvedToken}`)
  }
  if (
    init.body !== undefined &&
    !(init.body instanceof FormData) &&
    !headers.has('Content-Type')
  ) {
    headers.set('Content-Type', 'application/json')
  }

  const url = path.startsWith('http')
    ? path
    : `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`

  const response = await fetch(url, { ...init, headers })

  if (!response.ok) {
    const body = await response.text()
    throw new ApiError(
      response.status,
      body || `Request failed with status ${response.status}`,
      body,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  const contentType = response.headers.get('Content-Type') ?? ''
  if (contentType.includes('application/json')) {
    return (await response.json()) as T
  }

  return (await response.text()) as T
}

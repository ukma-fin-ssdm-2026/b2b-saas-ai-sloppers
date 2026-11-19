import { apiFetch } from './client'

export { ApiError } from './client'

export type TokenResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export type UserResponse = {
  id: string
  email: string
  globalAdmin: boolean
  headOf: string[]
  /** Необов'язкові: користувач може задати їх пізніше (FR-069) */
  firstName?: string
  lastName?: string
}

export type UpdateProfileRequest = {
  firstName?: string
  lastName?: string
}

export async function loginApi(
  email: string,
  password: string,
): Promise<TokenResponse> {
  return apiFetch<TokenResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
    skipAuth: true,
  })
}

export async function registerApi(
  email: string,
  password: string,
): Promise<UserResponse> {
  return apiFetch<UserResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
    skipAuth: true,
  })
}

export async function updateProfileApi(
  body: UpdateProfileRequest,
): Promise<UserResponse> {
  return apiFetch<UserResponse>('/auth/me', {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}

export async function verifyApi(email: string, code: string): Promise<void> {
  await apiFetch<void>('/auth/verify', {
    method: 'POST',
    body: JSON.stringify({ email, code }),
    skipAuth: true,
  })
}

export async function resendCodeApi(email: string): Promise<void> {
  await apiFetch<void>('/auth/resend-code', {
    method: 'POST',
    body: JSON.stringify({ email }),
    skipAuth: true,
  })
}

export async function meApi(token?: string): Promise<UserResponse> {
  return apiFetch<UserResponse>('/auth/me', { token })
}

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
  firstName: string | null
  lastName: string | null
  status: string
  globalAdmin: boolean
  headOf: string[]
}

export type RegisterPayload = {
  email: string
  password: string
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
  payload: RegisterPayload,
): Promise<UserResponse> {
  return apiFetch<UserResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify({
      email: payload.email,
      password: payload.password,
      firstName: payload.firstName?.trim() || null,
      lastName: payload.lastName?.trim() || null,
    }),
    skipAuth: true,
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

export async function requestPasswordResetApi(email: string): Promise<void> {
  await apiFetch<void>('/auth/password-reset', {
    method: 'POST',
    body: JSON.stringify({ email }),
    skipAuth: true,
  })
}

export async function confirmPasswordResetApi(
  email: string,
  code: string,
  newPassword: string,
): Promise<void> {
  await apiFetch<void>('/auth/password-reset/confirm', {
    method: 'POST',
    body: JSON.stringify({ email, code, newPassword }),
    skipAuth: true,
  })
}

export async function meApi(token?: string): Promise<UserResponse> {
  return apiFetch<UserResponse>('/auth/me', { token })
}

export async function updateProfileApi(
  firstName: string,
  lastName: string,
): Promise<UserResponse> {
  return apiFetch<UserResponse>('/auth/me', {
    method: 'PATCH',
    body: JSON.stringify({ firstName, lastName }),
  })
}

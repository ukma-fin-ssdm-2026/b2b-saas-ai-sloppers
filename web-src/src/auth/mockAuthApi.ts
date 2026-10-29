/** Front-end mock of US-001 until backend Auth exists. Stored in localStorage. */

const USERS_KEY = 'community-mock-users'
const PENDING_KEY = 'community-mock-pending'

const UKMA_EMAIL = /^[^@\s]+@ukma\.edu\.ua$/i
const CODE_TTL_MS = 15 * 60 * 1000
const MAX_ATTEMPTS = 5

export type MockUser = {
  email: string
  password: string
  active: boolean
}

export type PendingRegistration = {
  email: string
  password: string
  code: string
  expiresAt: number
  attempts: number
}

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return fallback
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

function writeJson(key: string, value: unknown): void {
  localStorage.setItem(key, JSON.stringify(value))
}

function listUsers(): MockUser[] {
  return readJson<MockUser[]>(USERS_KEY, [])
}

function saveUsers(users: MockUser[]): void {
  writeJson(USERS_KEY, users)
}

function listPending(): PendingRegistration[] {
  return readJson<PendingRegistration[]>(PENDING_KEY, [])
}

function savePending(items: PendingRegistration[]): void {
  writeJson(PENDING_KEY, items)
}

function randomCode(): string {
  return String(Math.floor(100000 + Math.random() * 900000))
}

export function isUkmaEmail(email: string): boolean {
  return UKMA_EMAIL.test(email.trim())
}

export type AuthResult<T> = { ok: true; data: T } | { ok: false; error: string }

export function registerMock(
  emailRaw: string,
  password: string,
): AuthResult<{ email: string; demoCode: string }> {
  const email = emailRaw.trim().toLowerCase()
  if (!isUkmaEmail(email)) {
    return {
      ok: false,
      error: 'Дозволена лише пошта @ukma.edu.ua',
    }
  }
  if (password.length < 6) {
    return { ok: false, error: 'Пароль має бути щонайменше 6 символів' }
  }
  if (listUsers().some((u) => u.email === email && u.active)) {
    return { ok: false, error: 'Цей email уже зареєстрований' }
  }

  const code = randomCode()
  const pending = listPending().filter((p) => p.email !== email)
  pending.push({
    email,
    password,
    code,
    expiresAt: Date.now() + CODE_TTL_MS,
    attempts: 0,
  })
  savePending(pending)

  const users = listUsers().filter((u) => u.email !== email)
  users.push({ email, password, active: false })
  saveUsers(users)

  return { ok: true, data: { email, demoCode: code } }
}

export function verifyMock(emailRaw: string, codeRaw: string): AuthResult<{ email: string }> {
  const email = emailRaw.trim().toLowerCase()
  const code = codeRaw.trim()
  const pending = listPending()
  const entry = pending.find((p) => p.email === email)
  if (!entry) {
    return { ok: false, error: 'Немає активного коду. Зареєструйтесь знову.' }
  }
  if (Date.now() > entry.expiresAt) {
    savePending(pending.filter((p) => p.email !== email))
    return { ok: false, error: 'Код прострочено. Запросіть новий через реєстрацію.' }
  }
  if (entry.attempts >= MAX_ATTEMPTS) {
    savePending(pending.filter((p) => p.email !== email))
    return { ok: false, error: 'Забагато спроб. Зареєструйтесь знову.' }
  }
  if (entry.code !== code) {
    entry.attempts += 1
    savePending(pending)
    return {
      ok: false,
      error: `Невірний код (спроб: ${entry.attempts}/${MAX_ATTEMPTS})`,
    }
  }

  savePending(pending.filter((p) => p.email !== email))
  const users = listUsers()
  const idx = users.findIndex((u) => u.email === email)
  if (idx >= 0) {
    users[idx] = { ...users[idx], active: true, password: entry.password }
  } else {
    users.push({ email, password: entry.password, active: true })
  }
  saveUsers(users)
  return { ok: true, data: { email } }
}

export function resendMockCode(emailRaw: string): AuthResult<{ demoCode: string }> {
  const email = emailRaw.trim().toLowerCase()
  const users = listUsers()
  const user = users.find((u) => u.email === email)
  if (!user || user.active) {
    return { ok: false, error: 'Немає неактивної реєстрації для цього email' }
  }
  const code = randomCode()
  const pending = listPending().filter((p) => p.email !== email)
  pending.push({
    email,
    password: user.password,
    code,
    expiresAt: Date.now() + CODE_TTL_MS,
    attempts: 0,
  })
  savePending(pending)
  return { ok: true, data: { demoCode: code } }
}

export function loginMock(
  loginRaw: string,
  password: string,
): AuthResult<{ email: string; role: 'admin' | 'user' }> {
  const login = loginRaw.trim().toLowerCase()
  const user = listUsers().find((u) => u.email === login)
  if (!user) {
    return { ok: false, error: 'Невірний логін або пароль' }
  }
  if (user.password !== password) {
    return { ok: false, error: 'Невірний логін або пароль' }
  }
  if (!user.active) {
    return {
      ok: false,
      error: 'Акаунт не активовано. Підтвердіть email кодом.',
    }
  }
  return { ok: true, data: { email: user.email, role: 'user' } }
}

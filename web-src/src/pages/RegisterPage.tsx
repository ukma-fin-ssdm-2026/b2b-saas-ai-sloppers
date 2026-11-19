import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError, registerApi } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import './OrganizationsPage.css'
import './panel.css'

function RegisterPage() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/admin" replace />
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    const trimmed = email.trim().toLowerCase()
    try {
      await registerApi(trimmed, password)
      navigate('/verify', {
        replace: true,
        state: {
          email: trimmed,
          hint: 'Код надіслано на пошту або записано в лог бекенда (якщо SMTP не налаштовано).',
        },
      })
    } catch (err) {
      if (err instanceof ApiError) {
        try {
          const parsed = JSON.parse(err.body) as {
            error?: { message?: string }
          }
          setError(parsed.error?.message || err.message)
        } catch {
          setError(err.message)
        }
      } else {
        setError('Не вдалося зареєструватися')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="panel-page">
      <div className="panel-page__inner">
        <p className="panel-page__eyebrow">Доступ</p>
        <h1 className="panel-page__title">Реєстрація</h1>
        <p className="panel-page__lead">
          Лише @ukma.edu.ua. Пароль від 8 символів. Після реєстрації
          підтвердіть email 6-значним кодом.
        </p>

        <form className="panel-form" onSubmit={(e) => void onSubmit(e)}>
          <div className="panel-field">
            <label htmlFor="reg-email">Email</label>
            <input
              id="reg-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              placeholder="name@ukma.edu.ua"
            />
          </div>
          <div className="panel-field">
            <label htmlFor="reg-pass">Пароль</label>
            <div className="panel-password">
              <input
                id="reg-pass"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
              />
              <button
                type="button"
                className="panel-password__toggle"
                onClick={() => setShowPassword((v) => !v)}
                aria-pressed={showPassword}
                aria-label={showPassword ? 'Сховати пароль' : 'Показати пароль'}
              >
                {showPassword ? 'Сховати' : 'Показати'}
              </button>
            </div>
          </div>
          {error ? <p className="panel-error">{error}</p> : null}
          <div className="panel-actions">
            <button type="submit" className="orgs-btn" disabled={submitting}>
              {submitting ? 'Реєстрація…' : 'Зареєструватися'}
            </button>
            <Link to="/login" className="orgs-btn">
              Уже є акаунт
            </Link>
          </div>
        </form>
      </div>
    </main>
  )
}

export default RegisterPage

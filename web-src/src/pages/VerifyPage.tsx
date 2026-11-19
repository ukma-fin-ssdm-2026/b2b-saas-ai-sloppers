import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, resendCodeApi, verifyApi } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import './OrganizationsPage.css'
import './panel.css'

type VerifyState = {
  email?: string
  hint?: string
}

function VerifyPage() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state as VerifyState | null) ?? {}

  const [email, setEmail] = useState(state.email ?? '')
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(state.hint ?? null)
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
      await verifyApi(trimmed, code.trim())
      navigate('/login', {
        replace: true,
        state: { verifiedEmail: trimmed },
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
        setError('Не вдалося підтвердити код')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const onResend = async () => {
    setError(null)
    setMessage(null)
    const trimmed = email.trim().toLowerCase()
    try {
      await resendCodeApi(trimmed)
      setMessage(
        'Якщо акаунт існує й не підтверджений — код надіслано (або в лог сервера).',
      )
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
        setError('Не вдалося надіслати код')
      }
    }
  }

  return (
    <main className="panel-page">
      <div className="panel-page__inner">
        <p className="panel-page__eyebrow">Доступ</p>
        <h1 className="panel-page__title">Підтвердження email</h1>
        <p className="panel-page__lead">
          Введіть 6-значний код з листа. Без SMTP код пишеться в лог бекенда.
        </p>

        <form className="panel-form" onSubmit={(e) => void onSubmit(e)}>
          <div className="panel-field">
            <label htmlFor="verify-email">Email</label>
            <input
              id="verify-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="panel-field">
            <label htmlFor="verify-code">Код</label>
            <input
              id="verify-code"
              inputMode="numeric"
              pattern="[0-9]{6}"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value)}
              required
            />
          </div>
          {error ? <p className="panel-error">{error}</p> : null}
          {message ? <p className="panel-ok">{message}</p> : null}
          <div className="panel-actions">
            <button type="submit" className="orgs-btn" disabled={submitting}>
              {submitting ? 'Перевірка…' : 'Підтвердити'}
            </button>
            <button
              type="button"
              className="orgs-btn"
              onClick={() => void onResend()}
            >
              Надіслати код знову
            </button>
            <Link to="/login" className="orgs-btn">
              До входу
            </Link>
          </div>
        </form>
      </div>
    </main>
  )
}

export default VerifyPage

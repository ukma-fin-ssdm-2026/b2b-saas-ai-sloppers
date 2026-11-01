import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import './OrganizationsPage.css'
import './panel.css'

function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()

  const fromState = (location.state as { from?: string; verifiedEmail?: string } | null)
  const fromQuery = searchParams.get('from')
  const from = fromState?.from ?? fromQuery ?? '/admin'
  const verifiedEmail = fromState?.verifiedEmail

  const [username, setUsername] = useState(verifiedEmail ?? '')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to={from.startsWith('/') ? from : '/admin'} replace />
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const result = await login(username, password)
      if (!result.ok) {
        setError(result.error)
        return
      }
      const target = from.startsWith('/') ? from : '/admin'
      navigate(target, { replace: true })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="panel-page">
      <div className="panel-page__inner">
        <p className="panel-page__eyebrow">Доступ</p>
        <h1 className="panel-page__title">Вхід</h1>
        {verifiedEmail ? (
          <p className="panel-ok">Email підтверджено. Увійдіть паролем.</p>
        ) : (
          <p className="panel-page__lead">
            Для адмінки потрібен реальний акаунт @ukma.edu.ua (JWT). Демо-логін
            лише для Lab.
          </p>
        )}

        <form className="panel-form" onSubmit={(e) => void onSubmit(e)}>
          <div className="panel-field">
            <label htmlFor="login-user">Email або логін</label>
            <input
              id="login-user"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          <div className="panel-field">
            <label htmlFor="login-pass">Пароль</label>
            <div className="panel-password">
              <input
                id="login-pass"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
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
              {submitting ? 'Вхід…' : 'Увійти'}
            </button>
            <Link to="/register" className="orgs-btn">
              Реєстрація
            </Link>
            <Link to="/verify" className="orgs-btn">
              Підтвердити код
            </Link>
            <Link to="/" className="orgs-btn">
              На сайт
            </Link>
          </div>
        </form>
      </div>
    </main>
  )
}

export default LoginPage

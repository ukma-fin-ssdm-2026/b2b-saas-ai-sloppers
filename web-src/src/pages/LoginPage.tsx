import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import './OrganizationsPage.css'
import './panel.css'

function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from =
    (location.state as { from?: string } | null)?.from ?? '/admin'

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  if (isAuthenticated) {
    return <Navigate to={from} replace />
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const result = login(username, password)
    if (!result.ok) {
      setError(result.error)
      return
    }
    navigate(from, { replace: true })
  }

  return (
    <main className="panel-page">
      <div className="panel-page__inner">
        <p className="panel-page__eyebrow">Доступ</p>
        <h1 className="panel-page__title">Вхід</h1>

        <form className="panel-form" onSubmit={onSubmit}>
          <div className="panel-field">
            <label htmlFor="login-user">Логін</label>
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
            <input
              id="login-pass"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          {error ? <p className="panel-error">{error}</p> : null}
          <div className="panel-actions">
            <button type="submit" className="orgs-btn">
              Увійти
            </button>
            <Link to="/organizations" className="orgs-btn">
              На сайт
            </Link>
          </div>
        </form>
      </div>
    </main>
  )
}

export default LoginPage

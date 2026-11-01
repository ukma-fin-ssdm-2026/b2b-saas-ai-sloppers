import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { registerMock } from '../auth/mockAuthApi'
import './OrganizationsPage.css'
import './panel.css'

function RegisterPage() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  if (isAuthenticated) {
    return <Navigate to="/admin" replace />
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    const result = registerMock(email, password)
    if (!result.ok) {
      setError(result.error)
      return
    }
    navigate('/verify', {
      replace: true,
      state: {
        email: result.data.email,
        demoCode: result.data.demoCode,
      },
    })
  }

  return (
    <main className="panel-page">
      <div className="panel-page__inner">
        <p className="panel-page__eyebrow">Доступ</p>
        <h1 className="panel-page__title">Реєстрація</h1>
        <p className="panel-page__lead">
          Лише @ukma.edu.ua. Після реєстрації потрібне підтвердження 6-значним
          кодом (поки mock на фронті).
        </p>

        <form className="panel-form" onSubmit={onSubmit}>
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
            <input
              id="reg-pass"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={6}
            />
          </div>
          {error ? <p className="panel-error">{error}</p> : null}
          <div className="panel-actions">
            <button type="submit" className="orgs-btn">
              Зареєструватися
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

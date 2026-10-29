import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { resendMockCode, verifyMock } from '../auth/mockAuthApi'
import './OrganizationsPage.css'
import './panel.css'

type VerifyState = {
  email?: string
  demoCode?: string
}

function VerifyPage() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state as VerifyState | null) ?? {}

  const [email, setEmail] = useState(state.email ?? '')
  const [code, setCode] = useState('')
  const [demoCode, setDemoCode] = useState(state.demoCode ?? '')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  if (isAuthenticated) {
    return <Navigate to="/admin" replace />
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    const result = verifyMock(email, code)
    if (!result.ok) {
      setError(result.error)
      return
    }
    navigate('/login', {
      replace: true,
      state: { verifiedEmail: result.data.email },
    })
  }

  const onResend = () => {
    setError(null)
    setMessage(null)
    const result = resendMockCode(email)
    if (!result.ok) {
      setError(result.error)
      return
    }
    setDemoCode(result.data.demoCode)
    setMessage('Новий код згенеровано')
  }

  return (
    <main className="panel-page">
      <div className="panel-page__inner">
        <p className="panel-page__eyebrow">Доступ</p>
        <h1 className="panel-page__title">Підтвердження email</h1>
        <p className="panel-page__lead">
          Введіть 6-значний код. У демо-режимі код показується нижче замість
          листа.
        </p>
        {demoCode ? (
          <p className="panel-banner">Демо-код: {demoCode}</p>
        ) : null}

        <form className="panel-form" onSubmit={onSubmit}>
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
            <button type="submit" className="orgs-btn">
              Підтвердити
            </button>
            <button type="button" className="orgs-btn" onClick={onResend}>
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

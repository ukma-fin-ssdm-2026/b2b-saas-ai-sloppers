import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import {
  confirmPasswordResetApi,
  requestPasswordResetApi,
} from '../api/auth'
import { errorMessage } from '../api/errorMessage'
import { useAuth } from '../auth/AuthContext'
import './OrganizationsPage.css'
import './panel.css'

function PasswordResetPage() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [step, setStep] = useState<'request' | 'confirm'>('request')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/profile" replace />
  }

  const onRequest = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setMessage(null)
    setSubmitting(true)
    const trimmed = email.trim().toLowerCase()
    try {
      await requestPasswordResetApi(trimmed)
      setEmail(trimmed)
      setStep('confirm')
      setMessage(
        'Якщо акаунт існує — код надіслано (або в лог бекенда без SMTP).',
      )
    } catch (err) {
      setError(errorMessage(err, 'Не вдалося надіслати код'))
    } finally {
      setSubmitting(false)
    }
  }

  const onConfirm = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setMessage(null)
    setSubmitting(true)
    try {
      await confirmPasswordResetApi(email, code.trim(), password)
      void navigate('/login', {
        replace: true,
        state: { verifiedEmail: email },
      })
    } catch (err) {
      setError(errorMessage(err, 'Не вдалося змінити пароль'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="panel-page">
      <div className="panel-page__inner">
        <p className="panel-page__eyebrow">Доступ</p>
        <h1 className="panel-page__title">Скидання пароля</h1>
        <p className="panel-page__lead">
          {step === 'request'
            ? 'Вкажіть @ukma.edu.ua — надішлемо 6-значний код.'
            : 'Введіть код і новий пароль (від 8 символів).'}
        </p>

        {step === 'request' ? (
          <form className="panel-form" onSubmit={(e) => void onRequest(e)}>
            <div className="panel-field">
              <label htmlFor="reset-email">Email</label>
              <input
                id="reset-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                placeholder="name@ukma.edu.ua"
              />
            </div>
            {error ? <p className="panel-error">{error}</p> : null}
            {message ? <p className="panel-ok">{message}</p> : null}
            <div className="panel-actions">
              <button type="submit" className="orgs-btn" disabled={submitting}>
                {submitting ? 'Надсилання…' : 'Надіслати код'}
              </button>
              <Link to="/login" className="orgs-btn">
                До входу
              </Link>
            </div>
          </form>
        ) : (
          <form className="panel-form" onSubmit={(e) => void onConfirm(e)}>
            <div className="panel-field">
              <label htmlFor="reset-email-2">Email</label>
              <input id="reset-email-2" type="email" value={email} readOnly />
            </div>
            <div className="panel-field">
              <label htmlFor="reset-code">Код</label>
              <input
                id="reset-code"
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value)}
                required
              />
            </div>
            <div className="panel-field">
              <label htmlFor="reset-pass">Новий пароль</label>
              <div className="panel-password">
                <input
                  id="reset-pass"
                  type={showPassword ? 'text' : 'password'}
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
                >
                  {showPassword ? 'Сховати' : 'Показати'}
                </button>
              </div>
            </div>
            {error ? <p className="panel-error">{error}</p> : null}
            {message ? <p className="panel-ok">{message}</p> : null}
            <div className="panel-actions">
              <button type="submit" className="orgs-btn" disabled={submitting}>
                {submitting ? 'Збереження…' : 'Змінити пароль'}
              </button>
              <button
                type="button"
                className="orgs-btn"
                onClick={() => setStep('request')}
              >
                Назад
              </button>
            </div>
          </form>
        )}
      </div>
    </main>
  )
}

export default PasswordResetPage

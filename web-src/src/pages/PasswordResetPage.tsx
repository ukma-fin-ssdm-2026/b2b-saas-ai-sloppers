import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { confirmPasswordResetApi, requestPasswordResetApi } from '../api/auth'
import { apiErrorMessage } from '../utils/apiErrorMessage'
import './OrganizationsPage.css'
import './panel.css'

type Step = 'request' | 'confirm'

function PasswordResetPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('request')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  async function sendCode() {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await requestPasswordResetApi(email.trim().toLowerCase())
      setStep('confirm')
      // бекенд відповідає 204 і для невідомої пошти, щоб не розкривати, хто зареєстрований
      setMessage('Якщо акаунт існує — код надіслано на цю пошту.')
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося надіслати код'))
    } finally {
      setBusy(false)
    }
  }

  function onRequest(e: FormEvent) {
    e.preventDefault()
    void sendCode()
  }

  async function onConfirm(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await confirmPasswordResetApi(
        email.trim().toLowerCase(),
        code.trim(),
        newPassword,
      )
      // navigate у React Router 7 повертає проміс; нам нема чого чекати, але це має бути явно
      void navigate('/login', {
        replace: true,
        state: { hint: 'Пароль змінено, увійдіть з новим.' },
      })
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося змінити пароль'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="panel-page">
      <div className="panel-page__inner">
        <p className="panel-page__eyebrow">Доступ</p>
        <h1 className="panel-page__title">Забули пароль</h1>
        <p className="panel-page__lead">
          {step === 'request'
            ? 'Введіть пошту — надішлемо 6-значний код.'
            : 'Введіть код з листа й новий пароль.'}
        </p>

        {step === 'request' ? (
          <form className="panel-form" onSubmit={onRequest}>
            <div className="panel-field">
              <label htmlFor="reset-email">Email</label>
              <input
                id="reset-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            {error ? <p className="panel-error">{error}</p> : null}
            <div className="panel-actions">
              <button type="submit" className="orgs-btn" disabled={busy}>
                {busy ? 'Надсилання…' : 'Надіслати код'}
              </button>
              <Link to="/login" className="orgs-btn">
                До входу
              </Link>
            </div>
          </form>
        ) : (
          <form className="panel-form" onSubmit={(e) => void onConfirm(e)}>
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
              <label htmlFor="reset-password">Новий пароль</label>
              <input
                id="reset-password"
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                minLength={8}
                maxLength={72}
                required
              />
            </div>
            <p className="panel-hint">Від 8 до 72 символів.</p>
            {error ? <p className="panel-error">{error}</p> : null}
            {message ? <p className="panel-ok">{message}</p> : null}
            <div className="panel-actions">
              <button type="submit" className="orgs-btn" disabled={busy}>
                {busy ? 'Збереження…' : 'Змінити пароль'}
              </button>
              <button
                type="button"
                className="orgs-btn"
                disabled={busy}
                onClick={() => void sendCode()}
              >
                Надіслати код знову
              </button>
            </div>
          </form>
        )}
        {step === 'request' && message ? (
          <p className="panel-ok">{message}</p>
        ) : null}
      </div>
    </main>
  )
}

export default PasswordResetPage

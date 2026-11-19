import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { meApi, updateProfileApi } from '../api/auth'
import { errorMessage } from '../api/errorMessage'
import { useAuth } from '../auth/AuthContext'
import './OrganizationsPage.css'
import './panel.css'

function ProfilePage() {
  const { isAuthenticated, session } = useAuth()
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState(session?.username ?? '')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [ok, setOk] = useState<string | null>(null)

  useEffect(() => {
    if (!isAuthenticated) return
    let cancelled = false
    ;(async () => {
      setLoading(true)
      try {
        const me = await meApi()
        if (cancelled) return
        setEmail(me.email)
        setFirstName(me.firstName ?? '')
        setLastName(me.lastName ?? '')
      } catch (err) {
        if (!cancelled) {
          setError(errorMessage(err, 'Не вдалося завантажити профіль'))
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [isAuthenticated])

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: '/profile' }} />
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setOk(null)
    setSubmitting(true)
    try {
      const updated = await updateProfileApi(firstName.trim(), lastName.trim())
      setFirstName(updated.firstName ?? '')
      setLastName(updated.lastName ?? '')
      setOk('Профіль збережено')
    } catch (err) {
      setError(errorMessage(err, 'Не вдалося зберегти профіль'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="panel-page">
      <div className="panel-page__inner">
        <p className="panel-page__eyebrow">Акаунт</p>
        <h1 className="panel-page__title">Профіль</h1>
        <p className="panel-page__lead">
          Ім’я та прізвище бачать інші на дошці оголошень.
        </p>

        {loading ? <p className="panel-page__lead">Завантаження…</p> : null}

        {!loading ? (
          <form className="panel-form" onSubmit={(e) => void onSubmit(e)}>
            <div className="panel-field">
              <label htmlFor="profile-email">Email</label>
              <input id="profile-email" value={email} disabled />
            </div>
            <div className="panel-field">
              <label htmlFor="profile-first">Ім’я</label>
              <input
                id="profile-first"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                required
                maxLength={50}
              />
            </div>
            <div className="panel-field">
              <label htmlFor="profile-last">Прізвище</label>
              <input
                id="profile-last"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                required
                maxLength={50}
              />
            </div>
            {error ? <p className="panel-error">{error}</p> : null}
            {ok ? <p className="panel-ok">{ok}</p> : null}
            <div className="panel-actions">
              <button type="submit" className="orgs-btn" disabled={submitting}>
                {submitting ? 'Збереження…' : 'Зберегти'}
              </button>
              <Link to="/board" className="orgs-btn">
                До дошки
              </Link>
            </div>
          </form>
        ) : null}
      </div>
    </main>
  )
}

export default ProfilePage

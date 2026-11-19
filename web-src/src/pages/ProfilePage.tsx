import { useEffect, useState, type FormEvent } from 'react'
import { meApi, updateProfileApi } from '../api/auth'
import { readSession } from '../auth/session'
import { apiErrorMessage } from '../utils/apiErrorMessage'
import './OrganizationsPage.css'
import './panel.css'

const NAME_HINT = 'Лише літери, апостроф, дефіс і пробіл; починається з літери'

function ProfilePage() {
  const [email, setEmail] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const me = await meApi(readSession()?.token ?? '')
        if (cancelled) return
        setEmail(me.email)
        setFirstName(me.firstName ?? '')
        setLastName(me.lastName ?? '')
      } catch (err) {
        if (!cancelled)
          setError(apiErrorMessage(err, 'Не вдалося завантажити профіль'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [])

  async function onSave(e: FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    setMessage(null)
    try {
      const updated = await updateProfileApi({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
      })
      setFirstName(updated.firstName ?? '')
      setLastName(updated.lastName ?? '')
      setMessage('Профіль збережено')
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося зберегти профіль'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <main className="panel-page">
      <div className="panel-page__inner">
        <p className="panel-page__eyebrow">Акаунт</p>
        <h1 className="panel-page__title">Профіль</h1>
        <p className="panel-page__lead">
          Ім'я та прізвище бачать автори оголошень, на які ви відгукуєтеся.
        </p>

        {loading ? <p className="panel-hint">Завантаження…</p> : null}

        <form className="panel-form" onSubmit={(e) => void onSave(e)}>
          <div className="panel-field">
            <label htmlFor="profile-email">Email</label>
            <input id="profile-email" value={email} readOnly disabled />
          </div>
          <div className="panel-grid">
            <div className="panel-field">
              <label htmlFor="profile-first">Ім'я</label>
              <input
                id="profile-first"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                maxLength={50}
                required
              />
            </div>
            <div className="panel-field">
              <label htmlFor="profile-last">Прізвище</label>
              <input
                id="profile-last"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                maxLength={50}
                required
              />
            </div>
          </div>
          <p className="panel-hint">{NAME_HINT}</p>
          {error ? <p className="panel-error">{error}</p> : null}
          {message ? <p className="panel-ok">{message}</p> : null}
          <div className="panel-actions">
            <button
              type="submit"
              className="orgs-btn"
              disabled={saving || loading}
            >
              {saving ? 'Збереження…' : 'Зберегти'}
            </button>
          </div>
        </form>
      </div>
    </main>
  )
}

export default ProfilePage

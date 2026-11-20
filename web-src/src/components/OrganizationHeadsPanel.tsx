import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { appointHead, listHeads, removeHead, roleLog } from '../api/heads'
import type { OrganizationHead, RoleChange } from '../types/head'
import { apiErrorMessage } from '../utils/apiErrorMessage'
import { formatEventDateTime } from '../utils/datetime'

const ACTION_LABEL: Record<RoleChange['action'], string> = {
  APPOINTED: 'призначено',
  REMOVED: 'знято',
}

function OrganizationHeadsPanel({
  organizationId,
}: {
  organizationId: string
}) {
  const [heads, setHeads] = useState<OrganizationHead[]>([])
  const [log, setLog] = useState<RoleChange[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [logOpen, setLogOpen] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [loadedHeads, loadedLog] = await Promise.all([
        listHeads(organizationId),
        roleLog(organizationId),
      ])
      setHeads(loadedHeads)
      setLog(loadedLog)
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося завантажити голів'))
    } finally {
      setLoading(false)
    }
  }, [organizationId])

  useEffect(() => {
    void load()
  }, [load])

  async function onAppoint(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await appointHead(organizationId, { email: email.trim() })
      setEmail('')
      setMessage('Голову призначено')
      await load()
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося призначити голову'))
    } finally {
      setBusy(false)
    }
  }

  async function onRemove(head: OrganizationHead) {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await removeHead(organizationId, head.userId)
      setMessage(`${head.email} більше не голова`)
      await load()
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося зняти голову'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel-media-card">
      <h3 className="panel-row__title">Голови</h3>

      {message ? <p className="panel-ok">{message}</p> : null}
      {error ? <p className="panel-error">{error}</p> : null}

      {loading ? (
        <p className="panel-hint">Завантаження…</p>
      ) : (
        <ul className="panel-list">
          {heads.map((head) => (
            <li className="panel-row" key={head.userId}>
              <span>
                {head.email}
                <span className="panel-hint">
                  {' '}
                  з {formatEventDateTime(head.appointedAt)}
                </span>
              </span>
              <button
                type="button"
                className="orgs-btn"
                disabled={busy}
                onClick={() => void onRemove(head)}
              >
                Зняти
              </button>
            </li>
          ))}
        </ul>
      )}

      <form className="panel-form" onSubmit={(e) => void onAppoint(e)}>
        <div className="panel-field">
          <label htmlFor="appoint-head-email">Призначити голову</label>
          <input
            id="appoint-head-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="head@ukma.edu.ua"
            required
          />
        </div>
        <div className="panel-actions">
          <button type="submit" className="orgs-btn" disabled={busy}>
            {busy ? 'Збереження…' : 'Призначити'}
          </button>
        </div>
      </form>
      <p className="panel-hint">
        Користувач має бути зареєстрований із підтвердженою поштою @ukma.edu.ua.
        Останнього голову зняти не можна.
      </p>

      <button
        type="button"
        className="orgs-btn"
        onClick={() => setLogOpen((open) => !open)}
      >
        {logOpen ? 'Сховати журнал ролей' : `Журнал ролей (${log.length})`}
      </button>
      {logOpen ? (
        <ul className="panel-list">
          {log.length === 0 ? (
            <li className="panel-hint">Журнал порожній</li>
          ) : (
            log.map((entry) => (
              <li className="panel-row" key={entry.id}>
                <span>
                  {entry.targetEmail} — {ACTION_LABEL[entry.action]}
                  {entry.actorEmail ? ` (${entry.actorEmail})` : ''}
                </span>
                <span className="panel-hint">
                  {formatEventDateTime(entry.createdAt)}
                </span>
              </li>
            ))
          )}
        </ul>
      ) : null}
    </section>
  )
}

export default OrganizationHeadsPanel

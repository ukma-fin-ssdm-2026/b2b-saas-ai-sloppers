import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  ApiError,
  createOrganization,
  listOrganizations,
} from '../../api/organizations'
import type { Organization } from '../../types/organization'

function AdminOrganizationsPage() {
  const [items, setItems] = useState<Organization[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [reload, setReload] = useState(0)

  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [description, setDescription] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const page = await listOrganizations({ limit: 100, offset: 0 })
        if (!cancelled) setItems(page.data)
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof ApiError
              ? `Не вдалося завантажити (${err.status})`
              : 'Не вдалося завантажити',
          )
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [reload])

  const onCreate = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    setMessage(null)
    try {
      const created = await createOrganization({
        name: name.trim(),
        slug: slug.trim(),
        description: description.trim() || undefined,
      })
      setName('')
      setSlug('')
      setDescription('')
      setMessage(`Створено: ${created.name}`)
      setReload((n) => n + 1)
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `Створення не вдалося (${err.status})`
          : 'Створення не вдалося',
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <section>
      <h2 className="panel-row__title">Створити організацію</h2>
      <form className="panel-form" onSubmit={(e) => void onCreate(e)}>
        <div className="panel-field">
          <label htmlFor="org-name">Назва</label>
          <input
            id="org-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            minLength={3}
          />
        </div>
        <div className="panel-field">
          <label htmlFor="org-slug">Slug</label>
          <input
            id="org-slug"
            value={slug}
            onChange={(e) => setSlug(e.target.value)}
            required
            minLength={3}
            pattern="[a-z0-9]+(-[a-z0-9]+)*"
            title="лише a-z, 0-9 і дефіс"
          />
        </div>
        <div className="panel-field">
          <label htmlFor="org-desc">Опис</label>
          <textarea
            id="org-desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>
        <div className="panel-actions">
          <button type="submit" className="orgs-btn" disabled={saving}>
            {saving ? 'Створюємо…' : 'Створити'}
          </button>
        </div>
      </form>

      {message ? <p className="panel-ok">{message}</p> : null}
      {error ? <p className="panel-error">{error}</p> : null}

      <h2 className="panel-row__title">Список</h2>
      {loading ? <p className="panel-row__meta">Завантаження…</p> : null}
      {!loading && items.length === 0 ? (
        <p className="panel-row__meta">Порожньо — створи організацію вище.</p>
      ) : null}
      <ul className="panel-list">
        {items.map((org) => (
          <li key={org.id}>
            <div className="panel-row">
              <div className="panel-row__main">
                <h3 className="panel-row__title">{org.name}</h3>
                <p className="panel-row__meta">
                  {org.slug}
                  {org.autoPublish ? ' · autoPublish' : ''}
                </p>
              </div>
              <div className="panel-row__actions">
                <Link className="orgs-btn" to={`/admin/organizations/${org.id}`}>
                  Профіль
                </Link>
                <Link
                  className="orgs-btn"
                  to={`/admin/organizations/${org.id}/events`}
                >
                  Події
                </Link>
              </div>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}

export default AdminOrganizationsPage

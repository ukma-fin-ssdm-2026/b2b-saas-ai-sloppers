import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import {
  deleteOrganization,
  deleteOrganizationLogo,
  getOrganization,
  updateOrganization,
  uploadOrganizationLogo,
} from '../../api/organizations'
import type { Organization } from '../../types/organization'

function AdminOrganizationDetailPage() {
  const { organizationId } = useParams<{ organizationId: string }>()
  const navigate = useNavigate()

  const [org, setOrg] = useState<Organization | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [reload, setReload] = useState(0)

  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [description, setDescription] = useState('')
  const [instagramHandle, setInstagramHandle] = useState('')
  const [contactEmail, setContactEmail] = useState('')
  const [autoPublish, setAutoPublish] = useState(false)

  useEffect(() => {
    if (!organizationId) return
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      try {
        const loaded = await getOrganization(organizationId!)
        if (cancelled) return
        setOrg(loaded)
        setName(loaded.name)
        setSlug(loaded.slug)
        setDescription(loaded.description ?? '')
        setInstagramHandle(loaded.instagramHandle ?? '')
        setContactEmail(loaded.contactEmail ?? '')
        setAutoPublish(loaded.autoPublish)
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof ApiError
              ? `Помилка (${err.status})`
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
  }, [organizationId, reload])

  const onSave = async (event: FormEvent) => {
    event.preventDefault()
    if (!organizationId) return
    setMessage(null)
    setError(null)
    try {
      const updated = await updateOrganization(organizationId, {
        name: name.trim(),
        slug: slug.trim(),
        description: description.trim() || undefined,
        instagramHandle: instagramHandle.trim() || undefined,
        contactEmail: contactEmail.trim() || undefined,
        autoPublish,
      })
      setOrg(updated)
      setMessage('Збережено')
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `Збереження не вдалося (${err.status})`
          : 'Збереження не вдалося',
      )
    }
  }

  const onDelete = async () => {
    if (!organizationId) return
    if (!window.confirm('Видалити організацію разом із подіями?')) return
    try {
      await deleteOrganization(organizationId)
      navigate('/admin/organizations')
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `Видалення не вдалося (${err.status})`
          : 'Видалення не вдалося',
      )
    }
  }

  const onLogo = async (file: File | null) => {
    if (!organizationId || !file) return
    try {
      const updated = await uploadOrganizationLogo(organizationId, file)
      setOrg(updated)
      setMessage('Логотип оновлено')
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `Логотип: помилка (${err.status})`
          : 'Логотип не завантажено',
      )
    }
  }

  const onRemoveLogo = async () => {
    if (!organizationId) return
    try {
      await deleteOrganizationLogo(organizationId)
      setReload((n) => n + 1)
      setMessage('Логотип видалено')
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `Видалення лого (${err.status})`
          : 'Не вдалося видалити лого',
      )
    }
  }

  if (loading) {
    return <p className="panel-row__meta">Завантаження…</p>
  }

  if (!org) {
    return (
      <div>
        <p className="panel-error">{error ?? 'Не знайдено'}</p>
        <Link to="/admin/organizations">← Назад</Link>
      </div>
    )
  }

  return (
    <section>
      <p className="panel-page__eyebrow">
        <Link to="/admin/organizations">← Організації</Link>
      </p>
      <h2 className="panel-row__title">{org.name}</h2>
      <div className="panel-actions" style={{ marginBottom: '1.25rem' }}>
        <Link
          className="orgs-btn"
          to={`/admin/organizations/${org.id}/events`}
        >
          Кабінет подій
        </Link>
      </div>
      {message ? <p className="panel-ok">{message}</p> : null}
      {error ? <p className="panel-error">{error}</p> : null}

      <form className="panel-form" onSubmit={(e) => void onSave(e)}>
        <div className="panel-field">
          <label htmlFor="edit-name">Назва</label>
          <input
            id="edit-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </div>
        <div className="panel-field">
          <label htmlFor="edit-slug">Slug</label>
          <input
            id="edit-slug"
            value={slug}
            onChange={(e) => setSlug(e.target.value)}
            required
          />
        </div>
        <div className="panel-field">
          <label htmlFor="edit-desc">Опис</label>
          <textarea
            id="edit-desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>
        <div className="panel-field">
          <label htmlFor="edit-ig">Instagram</label>
          <input
            id="edit-ig"
            value={instagramHandle}
            onChange={(e) => setInstagramHandle(e.target.value)}
          />
        </div>
        <div className="panel-field">
          <label htmlFor="edit-email">Email</label>
          <input
            id="edit-email"
            type="email"
            value={contactEmail}
            onChange={(e) => setContactEmail(e.target.value)}
          />
        </div>
        <label className="panel-check">
          <input
            type="checkbox"
            checked={autoPublish}
            onChange={(e) => setAutoPublish(e.target.checked)}
          />
          autoPublish (спаршені події без модерації)
        </label>
        <div className="panel-actions">
          <button type="submit" className="orgs-btn">
            Зберегти (PATCH)
          </button>
          <button
            type="button"
            className="orgs-btn"
            onClick={() => void onDelete()}
          >
            Видалити
          </button>
        </div>
      </form>

      <h3 className="panel-row__title">Логотип</h3>
      {org.logoUrl ? (
        <p className="panel-row__meta">
          <a href={org.logoUrl} target="_blank" rel="noreferrer">
            поточний файл
          </a>
        </p>
      ) : (
        <p className="panel-row__meta">Немає логотипу</p>
      )}
      <div className="panel-actions">
        <label className="orgs-btn">
          Завантажити
          <input
            type="file"
            accept="image/png,image/jpeg,image/webp"
            hidden
            onChange={(e) => void onLogo(e.target.files?.[0] ?? null)}
          />
        </label>
        {org.logoUrl ? (
          <button
            type="button"
            className="orgs-btn"
            onClick={() => void onRemoveLogo()}
          >
            Прибрати
          </button>
        ) : null}
      </div>
    </section>
  )
}

export default AdminOrganizationDetailPage

import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  deleteOrganization,
  deleteOrganizationLogo,
  getOrganization,
  updateOrganization,
  uploadOrganizationLogo,
} from '../../api/organizations'
import type { Organization } from '../../types/organization'
import { apiErrorMessage } from '../../utils/apiErrorMessage'

function AdminOrganizationDetailPage() {
  const { organizationId } = useParams<{ organizationId: string }>()
  const navigate = useNavigate()

  const [org, setOrg] = useState<Organization | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)

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
          setError(apiErrorMessage(err, 'Не вдалося завантажити'))
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [organizationId])

  const onSave = async (event: FormEvent) => {
    event.preventDefault()
    if (!organizationId) return
    setSaving(true)
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
      setError(apiErrorMessage(err, 'Збереження не вдалося'))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async () => {
    if (!organizationId) return
    if (!window.confirm('Видалити організацію разом із подіями?')) return
    try {
      await deleteOrganization(organizationId)
      navigate('/admin/organizations')
    } catch (err) {
      setError(apiErrorMessage(err, 'Видалення не вдалося'))
    }
  }

  const onLogo = async (file: File | null) => {
    if (!organizationId || !file) return
    setUploading(true)
    setError(null)
    try {
      const updated = await uploadOrganizationLogo(organizationId, file)
      setOrg(updated)
      setMessage('Логотип оновлено — перевір карусель на /organizations')
    } catch (err) {
      setError(apiErrorMessage(err, 'Логотип не завантажено'))
    } finally {
      setUploading(false)
    }
  }

  const onRemoveLogo = async () => {
    if (!organizationId) return
    setUploading(true)
    setError(null)
    try {
      await deleteOrganizationLogo(organizationId)
      const refreshed = await getOrganization(organizationId)
      setOrg(refreshed)
      setMessage('Логотип видалено')
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося видалити лого'))
    } finally {
      setUploading(false)
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
        <Link className="orgs-btn" to={`/organizations/${org.id}`}>
          На сайті
        </Link>
      </div>
      {message ? <p className="panel-ok">{message}</p> : null}
      {error ? <p className="panel-error">{error}</p> : null}

      <div className="panel-split">
        <form className="panel-form" onSubmit={(e) => void onSave(e)}>
          <h3 className="panel-row__title">Профіль</h3>
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
          <div className="panel-grid">
            <div className="panel-field">
              <label htmlFor="edit-ig">Instagram</label>
              <input
                id="edit-ig"
                value={instagramHandle}
                onChange={(e) => setInstagramHandle(e.target.value)}
                placeholder="fido_naukma"
              />
            </div>
            <div className="panel-field">
              <label htmlFor="edit-email">Контактний email</label>
              <input
                id="edit-email"
                type="email"
                value={contactEmail}
                onChange={(e) => setContactEmail(e.target.value)}
              />
            </div>
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
            <button type="submit" className="orgs-btn" disabled={saving}>
              {saving ? 'Збереження…' : 'Зберегти'}
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

        <div className="panel-media-card">
          <h3 className="panel-row__title">Логотип</h3>
          {org.logoUrl ? (
            <img
              className="panel-thumb panel-thumb--xl"
              src={org.logoUrl}
              alt={`Логотип ${org.name}`}
            />
          ) : (
            <span className="panel-thumb panel-thumb--xl panel-thumb--empty">
              {org.name.charAt(0)}
            </span>
          )}
          <div className="panel-actions">
            <label className="orgs-btn">
              {uploading ? 'Завантаження…' : 'Завантажити'}
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                hidden
                disabled={uploading}
                onChange={(e) => {
                  void onLogo(e.target.files?.[0] ?? null)
                  e.target.value = ''
                }}
              />
            </label>
            {org.logoUrl ? (
              <button
                type="button"
                className="orgs-btn"
                disabled={uploading}
                onClick={() => void onRemoveLogo()}
              >
                Прибрати
              </button>
            ) : null}
          </div>
          <p className="panel-hint">PNG / JPEG / WebP, до ~5&nbsp;MB.</p>
        </div>
      </div>
    </section>
  )
}

export default AdminOrganizationDetailPage

import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  createOrganization,
  listOrganizations,
  uploadOrganizationLogo,
} from '../../api/organizations'
import { useAuth } from '../../auth/AuthContext'
import type { Organization } from '../../types/organization'
import { apiErrorMessage, suggestSlug } from '../../utils/apiErrorMessage'

function AdminOrganizationsPage() {
  const navigate = useNavigate()
  const { session } = useAuth()
  const [items, setItems] = useState<Organization[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [reload, setReload] = useState(0)

  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [slugTouched, setSlugTouched] = useState(false)
  const [headEmail, setHeadEmail] = useState(
    () => session?.username?.includes('@') ? session.username : '',
  )
  const [description, setDescription] = useState('')
  const [logoFile, setLogoFile] = useState<File | null>(null)
  const [logoPreview, setLogoPreview] = useState<string | null>(null)
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
          setError(apiErrorMessage(err, 'Не вдалося завантажити організації'))
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

  useEffect(() => {
    if (!logoFile) {
      setLogoPreview(null)
      return
    }
    const url = URL.createObjectURL(logoFile)
    setLogoPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [logoFile])

  const onNameChange = (value: string) => {
    setName(value)
    if (!slugTouched) {
      setSlug(suggestSlug(value))
    }
  }

  const onCreate = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    setMessage(null)
    try {
      const created = await createOrganization({
        name: name.trim(),
        slug: slug.trim(),
        headEmail: headEmail.trim(),
        description: description.trim() || undefined,
      })

      if (logoFile) {
        try {
          await uploadOrganizationLogo(created.id, logoFile)
          setMessage(`Створено з логотипом: ${created.name}`)
        } catch (logoErr) {
          setMessage(
            `Організацію створено, але логотип не завантажився: ${apiErrorMessage(logoErr, 'помилка')}`,
          )
        }
      } else {
        setMessage(`Створено: ${created.name}`)
      }

      setName('')
      setSlug('')
      setSlugTouched(false)
      setDescription('')
      setLogoFile(null)
      setReload((n) => n + 1)
      navigate(`/admin/organizations/${created.id}`)
    } catch (err) {
      setError(apiErrorMessage(err, 'Створення не вдалося'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section>
      <h2 className="panel-row__title">Нова організація</h2>
      <p className="panel-page__lead">
        Після створення можна одразу додати логотип — він піде в публічну
        карусель.
      </p>

      <form className="panel-form" onSubmit={(e) => void onCreate(e)}>
        <div className="panel-grid">
          <div className="panel-field">
            <label htmlFor="org-name">Назва</label>
            <input
              id="org-name"
              value={name}
              onChange={(e) => onNameChange(e.target.value)}
              required
              minLength={3}
              placeholder="FIDo"
            />
          </div>
          <div className="panel-field">
            <label htmlFor="org-slug">Slug</label>
            <input
              id="org-slug"
              value={slug}
              onChange={(e) => {
                setSlugTouched(true)
                setSlug(e.target.value)
              }}
              required
              minLength={3}
              pattern="[a-z0-9]+(-[a-z0-9]+)*"
              title="лише a-z, 0-9 і дефіс"
              placeholder="fido"
            />
          </div>
        </div>

        <div className="panel-field">
          <label htmlFor="org-head">Email першого голови</label>
          <input
            id="org-head"
            type="email"
            value={headEmail}
            onChange={(e) => setHeadEmail(e.target.value)}
            required
            placeholder="head@ukma.edu.ua"
          />
          <span className="panel-hint">
            Має бути вже зареєстрований і підтверджений @ukma.edu.ua (можна ти).
          </span>
        </div>

        <div className="panel-field">
          <label htmlFor="org-desc">Опис</label>
          <textarea
            id="org-desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Студентська IT-спільнота…"
          />
        </div>

        <div className="panel-field">
          <label htmlFor="org-logo">Логотип (опційно)</label>
          <input
            id="org-logo"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            onChange={(e) => setLogoFile(e.target.files?.[0] ?? null)}
          />
          {logoPreview ? (
            <img
              className="panel-thumb panel-thumb--lg"
              src={logoPreview}
              alt="Попередній перегляд логотипу"
            />
          ) : null}
        </div>

        <div className="panel-actions">
          <button type="submit" className="orgs-btn" disabled={saving}>
            {saving ? 'Створюємо…' : 'Створити організацію'}
          </button>
        </div>
      </form>

      {message ? <p className="panel-ok">{message}</p> : null}
      {error ? <p className="panel-error">{error}</p> : null}

      <h2 className="panel-row__title">Каталог</h2>
      {loading ? <p className="panel-row__meta">Завантаження…</p> : null}
      {!loading && items.length === 0 ? (
        <p className="panel-row__meta">Порожньо — створи організацію вище.</p>
      ) : null}
      <ul className="panel-list">
        {items.map((org) => (
          <li key={org.id}>
            <div className="panel-row">
              <div className="panel-row__media">
                {org.logoUrl ? (
                  <img
                    className="panel-thumb"
                    src={org.logoUrl}
                    alt=""
                    loading="lazy"
                  />
                ) : (
                  <span className="panel-thumb panel-thumb--empty">
                    {org.name.charAt(0)}
                  </span>
                )}
              </div>
              <div className="panel-row__main">
                <h3 className="panel-row__title">{org.name}</h3>
                <p className="panel-row__meta">
                  {org.slug}
                  {org.autoPublish ? ' · autoPublish' : ''}
                  {org.logoUrl ? ' · є лого' : ' · без лого'}
                </p>
              </div>
              <div className="panel-row__actions">
                <Link className="orgs-btn" to={`/admin/organizations/${org.id}`}>
                  Редагувати
                </Link>
                <Link
                  className="orgs-btn"
                  to={`/admin/organizations/${org.id}/events`}
                >
                  Події
                </Link>
                <Link className="orgs-btn" to={`/organizations/${org.id}`}>
                  На сайті
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

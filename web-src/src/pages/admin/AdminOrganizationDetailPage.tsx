import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ApiError,
  approveEvent,
  createEvent,
  listOrganizationEvents,
  rejectEvent,
  unpublishEvent,
  updateEvent,
} from '../../api/events'
import {
  deleteOrganization,
  deleteOrganizationLogo,
  getOrganization,
  updateOrganization,
  uploadOrganizationLogo,
} from '../../api/organizations'
import type { Event } from '../../types/event'
import type { Organization } from '../../types/organization'
import { formatEventDateTime } from '../../utils/datetime'

function AdminOrganizationDetailPage() {
  const { organizationId } = useParams<{ organizationId: string }>()
  const navigate = useNavigate()

  const [org, setOrg] = useState<Organization | null>(null)
  const [events, setEvents] = useState<Event[]>([])
  const [statusFilter, setStatusFilter] = useState<string>('PENDING')
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

  const [eventTitle, setEventTitle] = useState('')
  const [eventStartsAt, setEventStartsAt] = useState('')
  const [eventLocation, setEventLocation] = useState('')

  const refreshEvents = useCallback(async (orgId: string, status: string) => {
    const page = await listOrganizationEvents(orgId, {
      status: status ? (status as Event['status']) : undefined,
      limit: 50,
      offset: 0,
    })
    setEvents(page.data)
  }, [])

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
        await refreshEvents(organizationId!, statusFilter)
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
  }, [organizationId, reload, refreshEvents, statusFilter])

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

  const onCreateEvent = async (event: FormEvent) => {
    event.preventDefault()
    if (!organizationId) return
    setError(null)
    try {
      await createEvent(organizationId, {
        title: eventTitle.trim(),
        startsAt: new Date(eventStartsAt).toISOString(),
        location: eventLocation.trim() || undefined,
      })
      setEventTitle('')
      setEventLocation('')
      setMessage('Подію створено (опубліковано)')
      setReload((n) => n + 1)
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `Подія: помилка (${err.status})`
          : 'Не вдалося створити подію',
      )
    }
  }

  const runEventAction = async (
    action: 'approve' | 'reject' | 'unpublish',
    eventId: string,
  ) => {
    try {
      if (action === 'approve') await approveEvent(eventId)
      if (action === 'reject') await rejectEvent(eventId)
      if (action === 'unpublish') await unpublishEvent(eventId)
      setMessage(`OK: ${action}`)
      if (organizationId) await refreshEvents(organizationId, statusFilter)
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `${action} не вдалося (${err.status})`
          : `${action} не вдалося`,
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
          autoPublish
        </label>
        <div className="panel-actions">
          <button type="submit" className="orgs-btn">
            Зберегти
          </button>
          <button type="button" className="orgs-btn" onClick={() => void onDelete()}>
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

      <h3 className="panel-row__title" style={{ marginTop: '2rem' }}>
        Створити подію
      </h3>
      <form className="panel-form" onSubmit={(e) => void onCreateEvent(e)}>
        <div className="panel-field">
          <label htmlFor="ev-title">Назва</label>
          <input
            id="ev-title"
            value={eventTitle}
            onChange={(e) => setEventTitle(e.target.value)}
            required
            minLength={3}
          />
        </div>
        <div className="panel-field">
          <label htmlFor="ev-start">Початок</label>
          <input
            id="ev-start"
            type="datetime-local"
            value={eventStartsAt}
            onChange={(e) => setEventStartsAt(e.target.value)}
            required
          />
        </div>
        <div className="panel-field">
          <label htmlFor="ev-loc">Локація</label>
          <input
            id="ev-loc"
            value={eventLocation}
            onChange={(e) => setEventLocation(e.target.value)}
          />
        </div>
        <button type="submit" className="orgs-btn">
          Створити
        </button>
      </form>

      <h3 className="panel-row__title">Події організації</h3>
      <div className="panel-field" style={{ marginBottom: '1rem' }}>
        <label htmlFor="status-filter">Фільтр статусу</label>
        <select
          id="status-filter"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
        >
          <option value="">Усі</option>
          <option value="PENDING">PENDING</option>
          <option value="PUBLISHED">PUBLISHED</option>
          <option value="REJECTED">REJECTED</option>
          <option value="UNPUBLISHED">UNPUBLISHED</option>
        </select>
      </div>

      <ul className="panel-list">
        {events.map((ev) => (
          <li key={ev.id}>
            <div className="panel-row">
              <div className="panel-row__main">
                <h4 className="panel-row__title">
                  <span className="panel-badge">{ev.status}</span>
                  {ev.title}
                </h4>
                <p className="panel-row__meta">
                  {formatEventDateTime(ev.startsAt)}
                  {ev.verified ? '' : ' · unverified'}
                  {ev.scraped ? ' · scraped' : ''}
                </p>
              </div>
              <div className="panel-row__actions">
                {ev.status === 'PENDING' ? (
                  <>
                    <button
                      type="button"
                      className="orgs-btn"
                      onClick={() => void runEventAction('approve', ev.id)}
                    >
                      Approve
                    </button>
                    <button
                      type="button"
                      className="orgs-btn"
                      onClick={() => void runEventAction('reject', ev.id)}
                    >
                      Reject
                    </button>
                  </>
                ) : null}
                {ev.status === 'PUBLISHED' ? (
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() => void runEventAction('unpublish', ev.id)}
                  >
                    Unpublish
                  </button>
                ) : null}
                {ev.status === 'PUBLISHED' && !ev.verified ? (
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() => void runEventAction('approve', ev.id)}
                  >
                    Verify
                  </button>
                ) : null}
                <button
                  type="button"
                  className="orgs-btn"
                  onClick={() => {
                    const next = window.prompt('Нова назва', ev.title)
                    if (!next || next === ev.title) return
                    void updateEvent(ev.id, { title: next })
                      .then(() => {
                        setMessage('Подію оновлено')
                        if (organizationId) {
                          return refreshEvents(organizationId, statusFilter)
                        }
                      })
                      .catch((err: unknown) => {
                        setError(
                          err instanceof ApiError
                            ? `PATCH (${err.status})`
                            : 'PATCH failed',
                        )
                      })
                  }}
                >
                  Rename
                </button>
              </div>
            </div>
          </li>
        ))}
      </ul>
      {events.length === 0 ? (
        <p className="panel-row__meta">Немає подій для цього фільтра.</p>
      ) : null}
    </section>
  )
}

export default AdminOrganizationDetailPage

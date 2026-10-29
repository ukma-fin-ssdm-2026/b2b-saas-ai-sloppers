import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import {
  approveEvent,
  createEvent,
  deleteEventImage,
  listOrganizationEvents,
  rejectEvent,
  unpublishEvent,
  updateEvent,
  uploadEventImage,
} from '../../api/events'
import { getOrganization } from '../../api/organizations'
import type { Event } from '../../types/event'
import type { Organization } from '../../types/organization'
import {
  formatEventDateTime,
  toDatetimeLocalValue,
} from '../../utils/datetime'

type QueueTab = 'pending' | 'unverified' | 'all'

function AdminOrganizationEventsPage() {
  const { organizationId } = useParams<{ organizationId: string }>()

  const [org, setOrg] = useState<Organization | null>(null)
  const [events, setEvents] = useState<Event[]>([])
  const [tab, setTab] = useState<QueueTab>('pending')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [editStartsAt, setEditStartsAt] = useState('')
  const [editLocation, setEditLocation] = useState('')
  const [editOriginalUrl, setEditOriginalUrl] = useState('')

  const [eventTitle, setEventTitle] = useState('')
  const [eventStartsAt, setEventStartsAt] = useState('')
  const [eventLocation, setEventLocation] = useState('')
  const [eventDescription, setEventDescription] = useState('')

  const loadEvents = useCallback(async (orgId: string, queue: QueueTab) => {
    if (queue === 'pending') {
      const page = await listOrganizationEvents(orgId, {
        status: 'PENDING',
        limit: 50,
        offset: 0,
      })
      setEvents(page.data)
      return
    }
    if (queue === 'unverified') {
      const page = await listOrganizationEvents(orgId, {
        verified: false,
        limit: 50,
        offset: 0,
      })
      setEvents(page.data)
      return
    }
    const page = await listOrganizationEvents(orgId, {
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
        await loadEvents(organizationId!, tab)
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
  }, [organizationId, tab, loadEvents])

  const refresh = async () => {
    if (!organizationId) return
    await loadEvents(organizationId, tab)
  }

  const startEdit = (ev: Event) => {
    setEditingId(ev.id)
    setEditTitle(ev.title)
    setEditDescription(ev.description ?? '')
    setEditStartsAt(toDatetimeLocalValue(ev.startsAt))
    setEditLocation(ev.location ?? '')
    setEditOriginalUrl(ev.originalUrl ?? '')
  }

  const onSaveEdit = async (event: FormEvent) => {
    event.preventDefault()
    if (!editingId) return
    setError(null)
    try {
      await updateEvent(editingId, {
        title: editTitle.trim(),
        description: editDescription.trim() || undefined,
        startsAt: new Date(editStartsAt).toISOString(),
        location: editLocation.trim() || undefined,
        originalUrl: editOriginalUrl.trim() || undefined,
      })
      setMessage('Подію оновлено (PATCH)')
      setEditingId(null)
      await refresh()
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `PATCH не вдався (${err.status})`
          : 'PATCH не вдався',
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
        description: eventDescription.trim() || undefined,
      })
      setEventTitle('')
      setEventLocation('')
      setEventDescription('')
      setMessage('Подію створено (опубліковано)')
      setTab('all')
      await loadEvents(organizationId, 'all')
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `Створення не вдалося (${err.status})`
          : 'Створення не вдалося',
      )
    }
  }

  const runAction = async (
    action: 'approve' | 'reject' | 'unpublish',
    eventId: string,
  ) => {
    setError(null)
    try {
      if (action === 'approve') await approveEvent(eventId)
      if (action === 'reject') await rejectEvent(eventId)
      if (action === 'unpublish') await unpublishEvent(eventId)
      setMessage(`OK: ${action}`)
      await refresh()
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `${action} (${err.status})`
          : `${action} не вдалося`,
      )
    }
  }

  const onUploadImage = async (eventId: string, file: File | null) => {
    if (!file) return
    setError(null)
    try {
      await uploadEventImage(eventId, file)
      setMessage('Картинку завантажено')
      await refresh()
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `Image upload (${err.status})`
          : 'Не вдалося завантажити картинку',
      )
    }
  }

  const onDeleteImage = async (eventId: string) => {
    setError(null)
    try {
      await deleteEventImage(eventId)
      setMessage('Картинку видалено')
      await refresh()
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `Image delete (${err.status})`
          : 'Не вдалося видалити картинку',
      )
    }
  }

  if (loading) {
    return <p className="panel-row__meta">Завантаження…</p>
  }

  if (!org || !organizationId) {
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
        <Link to={`/admin/organizations/${organizationId}`}>
          ← {org.name}
        </Link>
      </p>
      <h2 className="panel-row__title">Кабінет подій</h2>
      <p className="panel-page__lead">
        Черга PENDING, неперевірені (verified=false), створення, PATCH, image.
      </p>
      {message ? <p className="panel-ok">{message}</p> : null}
      {error ? <p className="panel-error">{error}</p> : null}

      <div className="panel-actions" style={{ marginBottom: '1.25rem' }}>
        <button
          type="button"
          className={`orgs-btn${tab === 'pending' ? ' panel-tab--active' : ''}`}
          onClick={() => setTab('pending')}
        >
          Черга PENDING
        </button>
        <button
          type="button"
          className={`orgs-btn${tab === 'unverified' ? ' panel-tab--active' : ''}`}
          onClick={() => setTab('unverified')}
        >
          verified=false
        </button>
        <button
          type="button"
          className={`orgs-btn${tab === 'all' ? ' panel-tab--active' : ''}`}
          onClick={() => setTab('all')}
        >
          Усі
        </button>
      </div>

      <ul className="panel-list">
        {events.map((ev) => (
          <li key={ev.id}>
            <div className="panel-row">
              <div className="panel-row__main">
                <h3 className="panel-row__title">
                  <span className="panel-badge">{ev.status}</span>
                  {!ev.verified ? (
                    <span className="panel-badge">unverified</span>
                  ) : null}
                  {ev.title}
                </h3>
                <p className="panel-row__meta">
                  {formatEventDateTime(ev.startsAt)}
                  {ev.location ? ` · ${ev.location}` : ''}
                  {ev.scraped ? ' · scraped' : ''}
                </p>
                {ev.imageUrl ? (
                  <p className="panel-row__meta">
                    <a href={ev.imageUrl} target="_blank" rel="noreferrer">
                      image
                    </a>
                  </p>
                ) : null}
              </div>
              <div className="panel-row__actions">
                {ev.status === 'PENDING' ? (
                  <>
                    <button
                      type="button"
                      className="orgs-btn"
                      onClick={() => void runAction('approve', ev.id)}
                    >
                      Approve
                    </button>
                    <button
                      type="button"
                      className="orgs-btn"
                      onClick={() => void runAction('reject', ev.id)}
                    >
                      Reject
                    </button>
                  </>
                ) : null}
                {ev.status === 'PUBLISHED' ? (
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() => void runAction('unpublish', ev.id)}
                  >
                    Unpublish
                  </button>
                ) : null}
                {ev.status === 'PUBLISHED' && !ev.verified ? (
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() => void runAction('approve', ev.id)}
                  >
                    Verify
                  </button>
                ) : null}
                <button
                  type="button"
                  className="orgs-btn"
                  onClick={() => startEdit(ev)}
                >
                  Edit
                </button>
                <label className="orgs-btn">
                  Image+
                  <input
                    type="file"
                    accept="image/png,image/jpeg,image/webp"
                    hidden
                    onChange={(e) =>
                      void onUploadImage(ev.id, e.target.files?.[0] ?? null)
                    }
                  />
                </label>
                {ev.imageUrl ? (
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() => void onDeleteImage(ev.id)}
                  >
                    Image−
                  </button>
                ) : null}
              </div>
            </div>

            {editingId === ev.id ? (
              <form
                className="panel-form"
                style={{ marginTop: '0.75rem' }}
                onSubmit={(e) => void onSaveEdit(e)}
              >
                <div className="panel-field">
                  <label>Назва</label>
                  <input
                    value={editTitle}
                    onChange={(e) => setEditTitle(e.target.value)}
                    required
                    minLength={3}
                  />
                </div>
                <div className="panel-field">
                  <label>Опис</label>
                  <textarea
                    value={editDescription}
                    onChange={(e) => setEditDescription(e.target.value)}
                  />
                </div>
                <div className="panel-field">
                  <label>Початок</label>
                  <input
                    type="datetime-local"
                    value={editStartsAt}
                    onChange={(e) => setEditStartsAt(e.target.value)}
                    required
                  />
                </div>
                <div className="panel-field">
                  <label>Локація</label>
                  <input
                    value={editLocation}
                    onChange={(e) => setEditLocation(e.target.value)}
                  />
                </div>
                <div className="panel-field">
                  <label>originalUrl</label>
                  <input
                    value={editOriginalUrl}
                    onChange={(e) => setEditOriginalUrl(e.target.value)}
                  />
                </div>
                <div className="panel-actions">
                  <button type="submit" className="orgs-btn">
                    Зберегти PATCH
                  </button>
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() => setEditingId(null)}
                  >
                    Скасувати
                  </button>
                </div>
              </form>
            ) : null}
          </li>
        ))}
      </ul>
      {events.length === 0 ? (
        <p className="panel-row__meta">Порожньо для цієї черги.</p>
      ) : null}

      <h3 className="panel-row__title" style={{ marginTop: '2rem' }}>
        Створити подію вручну
      </h3>
      <form className="panel-form" onSubmit={(e) => void onCreateEvent(e)}>
        <div className="panel-field">
          <label htmlFor="new-title">Назва</label>
          <input
            id="new-title"
            value={eventTitle}
            onChange={(e) => setEventTitle(e.target.value)}
            required
            minLength={3}
          />
        </div>
        <div className="panel-field">
          <label htmlFor="new-start">Початок</label>
          <input
            id="new-start"
            type="datetime-local"
            value={eventStartsAt}
            onChange={(e) => setEventStartsAt(e.target.value)}
            required
          />
        </div>
        <div className="panel-field">
          <label htmlFor="new-loc">Локація</label>
          <input
            id="new-loc"
            value={eventLocation}
            onChange={(e) => setEventLocation(e.target.value)}
          />
        </div>
        <div className="panel-field">
          <label htmlFor="new-desc">Опис</label>
          <textarea
            id="new-desc"
            value={eventDescription}
            onChange={(e) => setEventDescription(e.target.value)}
          />
        </div>
        <button type="submit" className="orgs-btn">
          Створити
        </button>
      </form>
    </section>
  )
}

export default AdminOrganizationEventsPage

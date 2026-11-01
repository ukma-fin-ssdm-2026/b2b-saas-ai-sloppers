import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
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
import { apiErrorMessage } from '../../utils/apiErrorMessage'
import {
  formatEventDateTime,
  toDatetimeLocalValue,
} from '../../utils/datetime'

type QueueTab = 'all' | 'pending' | 'unverified'

function AdminOrganizationEventsPage() {
  const { organizationId } = useParams<{ organizationId: string }>()

  const [org, setOrg] = useState<Organization | null>(null)
  const [events, setEvents] = useState<Event[]>([])
  const [tab, setTab] = useState<QueueTab>('all')
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
  const [eventImage, setEventImage] = useState<File | null>(null)
  const [eventImagePreview, setEventImagePreview] = useState<string | null>(
    null,
  )
  const [creating, setCreating] = useState(false)

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
  }, [organizationId, tab, loadEvents])

  useEffect(() => {
    if (!eventImage) {
      setEventImagePreview(null)
      return
    }
    const url = URL.createObjectURL(eventImage)
    setEventImagePreview(url)
    return () => URL.revokeObjectURL(url)
  }, [eventImage])

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
      setMessage('Подію оновлено')
      setEditingId(null)
      await refresh()
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося зберегти'))
    }
  }

  const onCreateEvent = async (event: FormEvent) => {
    event.preventDefault()
    if (!organizationId) return
    setCreating(true)
    setError(null)
    setMessage(null)
    try {
      const created = await createEvent(organizationId, {
        title: eventTitle.trim(),
        startsAt: new Date(eventStartsAt).toISOString(),
        location: eventLocation.trim() || undefined,
        description: eventDescription.trim() || undefined,
      })

      if (eventImage) {
        try {
          await uploadEventImage(created.id, eventImage)
          setMessage(
            `Подію «${created.title}» створено з афішею — перевір головну`,
          )
        } catch (imgErr) {
          setMessage(
            `Подію створено, але афіша не завантажилась: ${apiErrorMessage(imgErr, 'помилка')}`,
          )
        }
      } else {
        setMessage(
          `Подію «${created.title}» створено (опубліковано). Додай афішу нижче.`,
        )
      }

      setEventTitle('')
      setEventLocation('')
      setEventDescription('')
      setEventImage(null)
      setTab('all')
      await loadEvents(organizationId, 'all')
    } catch (err) {
      setError(apiErrorMessage(err, 'Створення не вдалося'))
    } finally {
      setCreating(false)
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
      setMessage(
        action === 'approve'
          ? 'Схвалено / верифіковано'
          : action === 'reject'
            ? 'Відхилено'
            : 'Знято з публікації',
      )
      await refresh()
    } catch (err) {
      setError(apiErrorMessage(err, 'Дію не виконано'))
    }
  }

  const onUploadImage = async (eventId: string, file: File | null) => {
    if (!file) return
    setError(null)
    try {
      await uploadEventImage(eventId, file)
      setMessage('Афішу завантажено — вона зʼявиться на головній')
      await refresh()
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося завантажити афішу'))
    }
  }

  const onDeleteImage = async (eventId: string) => {
    setError(null)
    try {
      await deleteEventImage(eventId)
      setMessage('Афішу видалено')
      await refresh()
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося видалити афішу'))
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
        Створені вручну події одразу PUBLISHED і показуються гостям. Афіша —
        окремим файлом (PNG/JPEG/WebP).
      </p>
      {message ? <p className="panel-ok">{message}</p> : null}
      {error ? <p className="panel-error">{error}</p> : null}

      <h3 className="panel-row__title">Нова подія</h3>
      <form className="panel-form" onSubmit={(e) => void onCreateEvent(e)}>
        <div className="panel-grid">
          <div className="panel-field">
            <label htmlFor="new-title">Назва</label>
            <input
              id="new-title"
              value={eventTitle}
              onChange={(e) => setEventTitle(e.target.value)}
              required
              minLength={3}
              placeholder="Хакатон Могилянки"
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
        </div>
        <div className="panel-field">
          <label htmlFor="new-loc">Локація</label>
          <input
            id="new-loc"
            value={eventLocation}
            onChange={(e) => setEventLocation(e.target.value)}
            placeholder="Корпус 1, ауд. 301"
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
        <div className="panel-field">
          <label htmlFor="new-image">Афіша (опційно)</label>
          <input
            id="new-image"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            onChange={(e) => setEventImage(e.target.files?.[0] ?? null)}
          />
          {eventImagePreview ? (
            <img
              className="panel-thumb panel-thumb--poster"
              src={eventImagePreview}
              alt="Попередній перегляд афіші"
            />
          ) : null}
        </div>
        <button type="submit" className="orgs-btn" disabled={creating}>
          {creating ? 'Створюємо…' : 'Створити подію'}
        </button>
      </form>

      <div className="panel-actions" style={{ marginBottom: '1.25rem' }}>
        <button
          type="button"
          className={`orgs-btn${tab === 'all' ? ' panel-tab--active' : ''}`}
          onClick={() => setTab('all')}
        >
          Усі
        </button>
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
          Неверифіковані
        </button>
      </div>

      <ul className="panel-list">
        {events.map((ev) => (
          <li key={ev.id}>
            <div className="panel-row">
              <div className="panel-row__media">
                {ev.imageUrl ? (
                  <img
                    className="panel-thumb panel-thumb--poster"
                    src={ev.imageUrl}
                    alt=""
                    loading="lazy"
                  />
                ) : (
                  <span className="panel-thumb panel-thumb--poster panel-thumb--empty">
                    —
                  </span>
                )}
              </div>
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
                </p>
              </div>
              <div className="panel-row__actions">
                {ev.status === 'PENDING' ? (
                  <>
                    <button
                      type="button"
                      className="orgs-btn"
                      onClick={() => void runAction('approve', ev.id)}
                    >
                      Схвалити
                    </button>
                    <button
                      type="button"
                      className="orgs-btn"
                      onClick={() => void runAction('reject', ev.id)}
                    >
                      Відхилити
                    </button>
                  </>
                ) : null}
                {ev.status === 'PUBLISHED' ? (
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() => void runAction('unpublish', ev.id)}
                  >
                    Зняти
                  </button>
                ) : null}
                {ev.status === 'PUBLISHED' && !ev.verified ? (
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() => void runAction('approve', ev.id)}
                  >
                    Верифікувати
                  </button>
                ) : null}
                <button
                  type="button"
                  className="orgs-btn"
                  onClick={() => startEdit(ev)}
                >
                  Редагувати
                </button>
                <label className="orgs-btn">
                  Афіша
                  <input
                    type="file"
                    accept="image/png,image/jpeg,image/webp"
                    hidden
                    onChange={(e) => {
                      void onUploadImage(ev.id, e.target.files?.[0] ?? null)
                      e.target.value = ''
                    }}
                  />
                </label>
                {ev.imageUrl ? (
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() => void onDeleteImage(ev.id)}
                  >
                    Прибрати афішу
                  </button>
                ) : null}
                <Link className="orgs-btn" to={`/events/${ev.id}`}>
                  На сайті
                </Link>
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
                  <label>Посилання (originalUrl)</label>
                  <input
                    value={editOriginalUrl}
                    onChange={(e) => setEditOriginalUrl(e.target.value)}
                  />
                </div>
                <div className="panel-actions">
                  <button type="submit" className="orgs-btn">
                    Зберегти
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
        <p className="panel-row__meta">Порожньо для цієї вкладки.</p>
      ) : null}
    </section>
  )
}

export default AdminOrganizationEventsPage

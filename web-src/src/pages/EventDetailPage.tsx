import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { eventIcsPath, getGoogleCalendarLink } from '../api/calendar'
import { ApiError, getEvent } from '../api/events'
import { getOrganization } from '../api/organizations'
import type { Event } from '../types/event'
import type { Organization } from '../types/organization'
import { formatEventDateTime } from '../utils/datetime'
import './EventDetailPage.css'
import './OrganizationsPage.css'

function EventDetailPage() {
  const { eventId } = useParams<{ eventId: string }>()

  const [event, setEvent] = useState<Event | null>(null)
  const [organization, setOrganization] = useState<Organization | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  const [calendarBusy, setCalendarBusy] = useState(false)
  const [calendarError, setCalendarError] = useState<string | null>(null)

  useEffect(() => {
    if (!eventId) {
      setEvent(null)
      setOrganization(null)
      setLoading(false)
      setError('Невірний ідентифікатор події')
      return
    }

    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      setEvent(null)
      setOrganization(null)
      setCalendarError(null)

      try {
        const loaded = await getEvent(eventId!)
        if (cancelled) return
        setEvent(loaded)

        try {
          const org = await getOrganization(loaded.organizationId)
          if (!cancelled) setOrganization(org)
        } catch {
          if (!cancelled) setOrganization(null)
        }
      } catch (err) {
        if (cancelled) return
        if (err instanceof ApiError && err.status === 404) {
          setError('Подію не знайдено або вона ще не опублікована')
        } else if (err instanceof ApiError) {
          setError(`Не вдалося завантажити подію (${err.status})`)
        } else {
          setError('Не вдалося завантажити подію')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [eventId, reloadToken])

  const openGoogleCalendar = useCallback(async () => {
    if (!eventId || calendarBusy) return
    setCalendarBusy(true)
    setCalendarError(null)

    try {
      const { url } = await getGoogleCalendarLink(eventId)
      window.open(url, '_blank', 'noopener,noreferrer')
    } catch (err) {
      const message =
        err instanceof ApiError
          ? `Не вдалося відкрити Google Calendar (${err.status})`
          : 'Не вдалося відкрити Google Calendar'
      setCalendarError(message)
    } finally {
      setCalendarBusy(false)
    }
  }, [calendarBusy, eventId])

  return (
    <main className="orgs-page event-detail">
      <div className="orgs-page__inner">
        <p className="orgs-page__eyebrow">
          <Link to="/events" className="event-detail__back">
            ← Події
          </Link>
        </p>

        {loading && <p className="orgs-page__status">Завантаження…</p>}

        {!loading && error && (
          <div className="orgs-page__status-block" role="alert">
            <p className="orgs-page__status orgs-page__status--error">{error}</p>
            <button
              type="button"
              className="orgs-btn"
              onClick={() => setReloadToken((n) => n + 1)}
            >
              Спробувати знову
            </button>
          </div>
        )}

        {!loading && event && (
          <>
            {event.imageUrl ? (
              <img
                className="event-detail__hero"
                src={event.imageUrl}
                alt=""
              />
            ) : null}

            <h1 className="orgs-page__title event-detail__title">
              {event.title}
            </h1>

            <dl className="event-detail__meta">
              <div className="event-detail__meta-row">
                <dt>Коли</dt>
                <dd>{formatEventDateTime(event.startsAt)}</dd>
              </div>
              {event.location ? (
                <div className="event-detail__meta-row">
                  <dt>Де</dt>
                  <dd>{event.location}</dd>
                </div>
              ) : null}
              <div className="event-detail__meta-row">
                <dt>Організація</dt>
                <dd>
                  <Link to={`/organizations/${event.organizationId}`}>
                    {organization?.name ?? 'Перейти до профілю'}
                  </Link>
                </dd>
              </div>
              {event.originalUrl ? (
                <div className="event-detail__meta-row">
                  <dt>Джерело</dt>
                  <dd>
                    <a
                      href={event.originalUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      Оригінальний анонс
                    </a>
                  </dd>
                </div>
              ) : null}
            </dl>

            {event.description ? (
              <p className="event-detail__description">{event.description}</p>
            ) : null}

            <div className="event-detail__actions">
              <button
                type="button"
                className="orgs-btn"
                onClick={() => void openGoogleCalendar()}
                disabled={calendarBusy}
              >
                {calendarBusy ? 'Відкриваємо…' : 'Додати в Google Calendar'}
              </button>
              <a
                className="orgs-btn event-detail__ics"
                href={eventIcsPath(event.id)}
                download={`event-${event.id}.ics`}
              >
                Завантажити .ics
              </a>
            </div>

            {calendarError && (
              <p
                className="orgs-page__status orgs-page__status--error"
                role="alert"
              >
                {calendarError}
              </p>
            )}
          </>
        )}
      </div>
    </main>
  )
}

export default EventDetailPage

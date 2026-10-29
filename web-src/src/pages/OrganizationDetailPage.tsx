import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, listEvents } from '../api/events'
import { getOrganization } from '../api/organizations'
import type { Event } from '../types/event'
import type { Organization } from '../types/organization'
import { formatEventDateTime } from '../utils/datetime'
import './OrganizationDetailPage.css'
import './OrganizationsPage.css'

const EVENTS_PAGE_SIZE = 20

function instagramUrl(handle: string): string {
  const clean = handle.replace(/^@/, '')
  return `https://instagram.com/${encodeURIComponent(clean)}`
}

function OrganizationDetailPage() {
  const { organizationId } = useParams<{ organizationId: string }>()

  const [organization, setOrganization] = useState<Organization | null>(null)
  const [orgLoading, setOrgLoading] = useState(true)
  const [orgError, setOrgError] = useState<string | null>(null)
  const [orgReload, setOrgReload] = useState(0)

  const [events, setEvents] = useState<Event[]>([])
  const [eventsHasMore, setEventsHasMore] = useState(false)
  const [eventsLoading, setEventsLoading] = useState(false)
  const [eventsLoadingMore, setEventsLoadingMore] = useState(false)
  const [eventsError, setEventsError] = useState<string | null>(null)
  const [eventsReload, setEventsReload] = useState(0)
  const eventsRequestIdRef = useRef(0)

  useEffect(() => {
    if (!organizationId) {
      setOrganization(null)
      setOrgLoading(false)
      setOrgError('Невірний ідентифікатор організації')
      return
    }

    let cancelled = false

    async function loadOrg() {
      setOrgLoading(true)
      setOrgError(null)
      setOrganization(null)

      try {
        const org = await getOrganization(organizationId!)
        if (!cancelled) setOrganization(org)
      } catch (err) {
        if (cancelled) return
        if (err instanceof ApiError && err.status === 404) {
          setOrgError('Організацію не знайдено')
        } else if (err instanceof ApiError) {
          setOrgError(`Не вдалося завантажити профіль (${err.status})`)
        } else {
          setOrgError('Не вдалося завантажити профіль')
        }
      } finally {
        if (!cancelled) setOrgLoading(false)
      }
    }

    void loadOrg()
    return () => {
      cancelled = true
    }
  }, [organizationId, orgReload])

  useEffect(() => {
    if (!organizationId || orgError || orgLoading) {
      setEvents([])
      setEventsHasMore(false)
      setEventsError(null)
      setEventsLoading(false)
      return
    }

    const requestId = ++eventsRequestIdRef.current
    let cancelled = false

    async function loadEvents() {
      setEventsLoading(true)
      setEventsError(null)
      setEvents([])
      setEventsHasMore(false)

      try {
        const page = await listEvents({
          organizationId,
          limit: EVENTS_PAGE_SIZE,
          offset: 0,
        })
        if (cancelled || requestId !== eventsRequestIdRef.current) return

        setEvents(page.data)
        setEventsHasMore(page.pagination.hasMore)
      } catch (err) {
        if (cancelled || requestId !== eventsRequestIdRef.current) return
        const message =
          err instanceof ApiError
            ? `Не вдалося завантажити події (${err.status})`
            : 'Не вдалося завантажити події'
        setEventsError(message)
      } finally {
        if (!cancelled && requestId === eventsRequestIdRef.current) {
          setEventsLoading(false)
        }
      }
    }

    void loadEvents()
    return () => {
      cancelled = true
    }
  }, [organizationId, orgError, orgLoading, eventsReload])

  const loadMoreEvents = useCallback(async () => {
    if (
      !organizationId ||
      eventsLoading ||
      eventsLoadingMore ||
      !eventsHasMore
    ) {
      return
    }

    const requestId = ++eventsRequestIdRef.current
    setEventsLoadingMore(true)
    setEventsError(null)

    try {
      const page = await listEvents({
        organizationId,
        limit: EVENTS_PAGE_SIZE,
        offset: events.length,
      })
      if (requestId !== eventsRequestIdRef.current) return

      setEvents((prev) => [...prev, ...page.data])
      setEventsHasMore(page.pagination.hasMore)
    } catch (err) {
      if (requestId !== eventsRequestIdRef.current) return
      const message =
        err instanceof ApiError
          ? `Не вдалося довантажити події (${err.status})`
          : 'Не вдалося довантажити події'
      setEventsError(message)
    } finally {
      if (requestId === eventsRequestIdRef.current) {
        setEventsLoadingMore(false)
      }
    }
  }, [
    events.length,
    eventsHasMore,
    eventsLoading,
    eventsLoadingMore,
    organizationId,
  ])

  return (
    <main className="orgs-page org-detail">
      <div className="orgs-page__inner">
        <p className="orgs-page__eyebrow">
          <Link to="/organizations" className="org-detail__back">
            ← Організації
          </Link>
        </p>

        {orgLoading && <p className="orgs-page__status">Завантаження…</p>}

        {!orgLoading && orgError && (
          <div className="orgs-page__status-block" role="alert">
            <p className="orgs-page__status orgs-page__status--error">
              {orgError}
            </p>
            <button
              type="button"
              className="orgs-btn"
              onClick={() => setOrgReload((n) => n + 1)}
            >
              Спробувати знову
            </button>
          </div>
        )}

        {!orgLoading && organization && (
          <>
            <header className="org-detail__header">
              <div className="org-detail__logo-wrap" aria-hidden={!organization.logoUrl}>
                {organization.logoUrl ? (
                  <img
                    className="org-detail__logo"
                    src={organization.logoUrl}
                    alt=""
                  />
                ) : (
                  <span className="org-detail__logo-fallback">
                    {organization.name.charAt(0).toUpperCase()}
                  </span>
                )}
              </div>
              <div className="org-detail__heading">
                <h1 className="orgs-page__title org-detail__title">
                  {organization.name}
                </h1>
                <p className="org-detail__slug">{organization.slug}</p>
              </div>
            </header>

            {organization.description ? (
              <p className="org-detail__description">{organization.description}</p>
            ) : (
              <p className="orgs-page__status">Опис поки не додано.</p>
            )}

            <dl className="org-detail__contacts">
              {organization.instagramHandle ? (
                <div className="org-detail__contact">
                  <dt>Instagram</dt>
                  <dd>
                    <a
                      href={instagramUrl(organization.instagramHandle)}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      @{organization.instagramHandle.replace(/^@/, '')}
                    </a>
                  </dd>
                </div>
              ) : null}
              {organization.contactEmail ? (
                <div className="org-detail__contact">
                  <dt>Email</dt>
                  <dd>
                    <a href={`mailto:${organization.contactEmail}`}>
                      {organization.contactEmail}
                    </a>
                  </dd>
                </div>
              ) : null}
              {!organization.instagramHandle && !organization.contactEmail ? (
                <p className="orgs-page__status">Контактів поки немає.</p>
              ) : null}
            </dl>

            <section className="org-detail__events" aria-labelledby="org-events-heading">
              <h2 id="org-events-heading" className="org-detail__section-title">
                Майбутні події
              </h2>

              {eventsLoading && (
                <p className="orgs-page__status">Завантаження подій…</p>
              )}

              {!eventsLoading && eventsError && events.length === 0 && (
                <div className="orgs-page__status-block" role="alert">
                  <p className="orgs-page__status orgs-page__status--error">
                    {eventsError}
                  </p>
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() => setEventsReload((n) => n + 1)}
                  >
                    Спробувати знову
                  </button>
                </div>
              )}

              {!eventsLoading && !eventsError && events.length === 0 && (
                <p className="orgs-page__status">
                  Немає запланованих опублікованих подій.
                </p>
              )}

              {events.length > 0 && (
                <>
                  <ul className="org-events-list">
                    {events.map((event) => (
                      <li key={event.id}>
                        <Link
                          to={`/events/${event.id}`}
                          className="org-event-card"
                        >
                          {event.imageUrl ? (
                            <img
                              className="org-event-card__image"
                              src={event.imageUrl}
                              alt=""
                              loading="lazy"
                            />
                          ) : (
                            <span className="org-event-card__image-fallback" />
                          )}
                          <div className="org-event-card__body">
                            <h3 className="org-event-card__title">
                              {event.title}
                            </h3>
                            <p className="org-event-card__meta">
                              {formatEventDateTime(event.startsAt)}
                            </p>
                            {event.location ? (
                              <p className="org-event-card__location">
                                {event.location}
                              </p>
                            ) : null}
                          </div>
                        </Link>
                      </li>
                    ))}
                  </ul>

                  {eventsError && (
                    <div className="orgs-page__status-block" role="alert">
                      <p className="orgs-page__status orgs-page__status--error">
                        {eventsError}
                      </p>
                      <button
                        type="button"
                        className="orgs-btn"
                        onClick={() => void loadMoreEvents()}
                      >
                        Спробувати знову
                      </button>
                    </div>
                  )}

                  {!eventsError && eventsHasMore && (
                    <div className="orgs-page__more">
                      <button
                        type="button"
                        className="orgs-btn"
                        onClick={() => void loadMoreEvents()}
                        disabled={eventsLoadingMore}
                      >
                        {eventsLoadingMore
                          ? 'Завантаження…'
                          : 'Показати ще'}
                      </button>
                    </div>
                  )}
                </>
              )}
            </section>
          </>
        )}
      </div>
    </main>
  )
}

export default OrganizationDetailPage

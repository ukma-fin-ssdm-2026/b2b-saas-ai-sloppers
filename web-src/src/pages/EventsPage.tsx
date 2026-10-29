import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError, listEvents } from '../api/events'
import { listOrganizations } from '../api/organizations'
import type { Event } from '../types/event'
import type { Organization } from '../types/organization'
import {
  dateInputToFromParam,
  dateInputToToParam,
  formatEventDateTime,
} from '../utils/datetime'
import './EventsPage.css'
import './OrganizationDetailPage.css'
import './OrganizationsPage.css'

const PAGE_SIZE = 20
const ORGS_FOR_FILTER_LIMIT = 100

function EventsPage() {
  const [searchParams, setSearchParams] = useSearchParams()

  const organizationId = searchParams.get('organizationId') ?? ''
  const fromDate = searchParams.get('from') ?? ''
  const toDate = searchParams.get('to') ?? ''

  const [orgOptions, setOrgOptions] = useState<Organization[]>([])
  const orgById = useMemo(() => {
    const map = new Map<string, Organization>()
    for (const org of orgOptions) map.set(org.id, org)
    return map
  }, [orgOptions])

  const [events, setEvents] = useState<Event[]>([])
  const [hasMore, setHasMore] = useState(false)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const requestIdRef = useRef(0)

  const filterError = useMemo(() => {
    if (fromDate && toDate && fromDate > toDate) {
      return 'Дата «до» має бути не раніше за дату «від».'
    }
    return null
  }, [fromDate, toDate])

  useEffect(() => {
    let cancelled = false

    async function loadOrgs() {
      try {
        const page = await listOrganizations({
          limit: ORGS_FOR_FILTER_LIMIT,
          offset: 0,
        })
        if (!cancelled) setOrgOptions(page.data)
      } catch {
        if (!cancelled) setOrgOptions([])
      }
    }

    void loadOrgs()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (filterError) {
      setEvents([])
      setHasMore(false)
      setTotal(0)
      setLoading(false)
      setError(null)
      return
    }

    const requestId = ++requestIdRef.current
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      setEvents([])
      setHasMore(false)
      setTotal(0)

      try {
        const page = await listEvents({
          limit: PAGE_SIZE,
          offset: 0,
          organizationId: organizationId || undefined,
          from: fromDate ? dateInputToFromParam(fromDate) : undefined,
          to: toDate ? dateInputToToParam(toDate) : undefined,
        })
        if (cancelled || requestId !== requestIdRef.current) return

        setEvents(page.data)
        setHasMore(page.pagination.hasMore)
        setTotal(page.pagination.total)
      } catch (err) {
        if (cancelled || requestId !== requestIdRef.current) return
        const message =
          err instanceof ApiError
            ? `Не вдалося завантажити події (${err.status})`
            : 'Не вдалося завантажити події'
        setError(message)
      } finally {
        if (!cancelled && requestId === requestIdRef.current) {
          setLoading(false)
        }
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [organizationId, fromDate, toDate, filterError, reloadToken])

  const loadMore = useCallback(async () => {
    if (loading || loadingMore || !hasMore || filterError) return

    const requestId = ++requestIdRef.current
    setLoadingMore(true)
    setError(null)

    try {
      const page = await listEvents({
        limit: PAGE_SIZE,
        offset: events.length,
        organizationId: organizationId || undefined,
        from: fromDate ? dateInputToFromParam(fromDate) : undefined,
        to: toDate ? dateInputToToParam(toDate) : undefined,
      })
      if (requestId !== requestIdRef.current) return

      setEvents((prev) => [...prev, ...page.data])
      setHasMore(page.pagination.hasMore)
      setTotal(page.pagination.total)
    } catch (err) {
      if (requestId !== requestIdRef.current) return
      const message =
        err instanceof ApiError
          ? `Не вдалося довантажити події (${err.status})`
          : 'Не вдалося довантажити події'
      setError(message)
    } finally {
      if (requestId === requestIdRef.current) {
        setLoadingMore(false)
      }
    }
  }, [
    events.length,
    filterError,
    fromDate,
    hasMore,
    loading,
    loadingMore,
    organizationId,
    toDate,
  ])

  const patchParams = useCallback(
    (patch: Record<string, string>) => {
      const next = new URLSearchParams(searchParams)
      for (const [key, value] of Object.entries(patch)) {
        if (value) next.set(key, value)
        else next.delete(key)
      }
      setSearchParams(next, { replace: true })
    },
    [searchParams, setSearchParams],
  )

  const clearFilters = useCallback(() => {
    setSearchParams({}, { replace: true })
  }, [setSearchParams])

  const hasActiveFilters = Boolean(organizationId || fromDate || toDate)
  const showEmpty =
    !loading && !error && !filterError && events.length === 0 && !loadingMore

  return (
    <main className="orgs-page events-page">
      <div className="orgs-page__inner">
        <p className="orgs-page__eyebrow">
          <Link to="/organizations" className="events-page__nav-link">
            Організації
          </Link>
          <span aria-hidden="true"> · </span>
          <span>Події</span>
        </p>
        <h1 className="orgs-page__title">Події</h1>

        <div className="events-filters">
          <label className="events-filters__field">
            <span className="events-filters__label">Організація</span>
            <select
              className="events-filters__control"
              value={organizationId}
              onChange={(event) =>
                patchParams({ organizationId: event.target.value })
              }
            >
              <option value="">Усі організації</option>
              {orgOptions.map((org) => (
                <option key={org.id} value={org.id}>
                  {org.name}
                </option>
              ))}
            </select>
          </label>

          <label className="events-filters__field">
            <span className="events-filters__label">Від</span>
            <input
              className="events-filters__control"
              type="date"
              value={fromDate}
              onChange={(event) => patchParams({ from: event.target.value })}
            />
          </label>

          <label className="events-filters__field">
            <span className="events-filters__label">До</span>
            <input
              className="events-filters__control"
              type="date"
              value={toDate}
              onChange={(event) => patchParams({ to: event.target.value })}
            />
          </label>

          {hasActiveFilters ? (
            <button
              type="button"
              className="orgs-btn events-filters__clear"
              onClick={clearFilters}
            >
              Скинути
            </button>
          ) : null}
        </div>

        {filterError && (
          <p className="orgs-page__status orgs-page__status--error" role="alert">
            {filterError}
          </p>
        )}

        {!loading && !error && !filterError && total > 0 ? (
          <p className="orgs-page__meta" aria-live="polite">
            Знайдено: {total}
          </p>
        ) : null}

        {loading && <p className="orgs-page__status">Завантаження…</p>}

        {!loading && error && events.length === 0 && (
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

        {showEmpty && (
          <p className="orgs-page__status">
            {hasActiveFilters
              ? 'За обраними фільтрами подій немає.'
              : 'Поки немає опублікованих майбутніх подій.'}
          </p>
        )}

        {events.length > 0 && (
          <>
            <ul className="org-events-list">
              {events.map((event) => {
                const org = orgById.get(event.organizationId)
                return (
                  <li key={event.id}>
                    <Link
                      to={`/events/${event.id}`}
                      className="org-event-card events-card"
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
                        <h2 className="org-event-card__title">{event.title}</h2>
                        <p className="org-event-card__meta">
                          {formatEventDateTime(event.startsAt)}
                        </p>
                        {event.location ? (
                          <p className="org-event-card__location">
                            {event.location}
                          </p>
                        ) : null}
                        <p className="events-card__org">
                          {org?.name ?? 'Організація'}
                        </p>
                      </div>
                    </Link>
                  </li>
                )
              })}
            </ul>

            {error && (
              <div className="orgs-page__status-block" role="alert">
                <p className="orgs-page__status orgs-page__status--error">
                  {error}
                </p>
                <button
                  type="button"
                  className="orgs-btn"
                  onClick={() => void loadMore()}
                >
                  Спробувати знову
                </button>
              </div>
            )}

            {!error && hasMore && (
              <div className="orgs-page__more">
                <button
                  type="button"
                  className="orgs-btn"
                  onClick={() => void loadMore()}
                  disabled={loadingMore}
                >
                  {loadingMore ? 'Завантаження…' : 'Показати ще'}
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </main>
  )
}

export default EventsPage

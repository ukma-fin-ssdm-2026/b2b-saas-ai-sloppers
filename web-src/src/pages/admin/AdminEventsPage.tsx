import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listEvents } from '../../api/events'
import { listOrganizations } from '../../api/organizations'
import type { Event } from '../../types/event'
import type { Organization } from '../../types/organization'
import { apiErrorMessage } from '../../utils/apiErrorMessage'
import { formatEventDateTime } from '../../utils/datetime'

function AdminEventsPage() {
  const [events, setEvents] = useState<Event[]>([])
  const [orgById, setOrgById] = useState<Map<string, Organization>>(new Map())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const [eventsPage, orgsPage] = await Promise.all([
          listEvents({ limit: 50, offset: 0 }),
          listOrganizations({ limit: 100, offset: 0 }),
        ])
        if (cancelled) return
        setEvents(eventsPage.data)
        const map = new Map<string, Organization>()
        for (const org of orgsPage.data) map.set(org.id, org)
        setOrgById(map)
      } catch (err) {
        if (!cancelled) {
          setError(apiErrorMessage(err, 'Не вдалося завантажити події'))
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <section>
      <h2 className="panel-row__title">Публічні події</h2>
      <p className="panel-page__lead">
        Те, що бачить гість на головній і в «Усі події». Щоб додати нову —
        зайди в кабінет організації.
      </p>
      {error ? <p className="panel-error">{error}</p> : null}
      {loading ? <p className="panel-row__meta">Завантаження…</p> : null}
      {!loading && events.length === 0 ? (
        <p className="panel-row__meta">
          Подій ще немає.{' '}
          <Link to="/admin/organizations">Створи організацію</Link> і додай
          подію з афішею.
        </p>
      ) : null}

      <ul className="panel-list">
        {events.map((ev) => {
          const org = orgById.get(ev.organizationId)
          return (
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
                    {ev.title}
                  </h3>
                  <p className="panel-row__meta">
                    {formatEventDateTime(ev.startsAt)}
                    {org ? ` · ${org.name}` : ''}
                    {ev.imageUrl ? ' · є афіша' : ' · без афіші'}
                  </p>
                </div>
                <div className="panel-row__actions">
                  <Link
                    className="orgs-btn"
                    to={`/admin/organizations/${ev.organizationId}/events`}
                  >
                    Кабінет
                  </Link>
                  <Link className="orgs-btn" to={`/events/${ev.id}`}>
                    На сайті
                  </Link>
                </div>
              </div>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

export default AdminEventsPage

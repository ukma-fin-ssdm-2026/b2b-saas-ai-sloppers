import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listEvents } from '../../api/events'
import { listOrganizations } from '../../api/organizations'
import { useAuth } from '../../auth/AuthContext'

function AdminHomePage() {
  const { isDemo, session } = useAuth()
  const [orgCount, setOrgCount] = useState<number | null>(null)
  const [eventCount, setEventCount] = useState<number | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const [orgs, events] = await Promise.all([
          listOrganizations({ limit: 1, offset: 0 }),
          listEvents({ limit: 1, offset: 0 }),
        ])
        if (!cancelled) {
          setOrgCount(orgs.pagination.total)
          setEventCount(events.pagination.total)
        }
      } catch {
        if (!cancelled) {
          setOrgCount(null)
          setEventCount(null)
        }
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <section>
      <p className="panel-page__lead">
        Швидкий стенд для тестових даних. Публічний каталог читає той самий
        `/api/v1`.
      </p>

      <div className="panel-stats">
        <div className="panel-stat">
          <span className="panel-stat__value">
            {orgCount === null ? '—' : orgCount}
          </span>
          <span className="panel-stat__label">організацій</span>
        </div>
        <div className="panel-stat">
          <span className="panel-stat__value">
            {eventCount === null ? '—' : eventCount}
          </span>
          <span className="panel-stat__label">подій (публічних)</span>
        </div>
      </div>

      <ol className="panel-steps">
        <li>
          Увійди JWT-акаунтом Global Admin
          {session && !isDemo ? ` (${session.username})` : ''}.
        </li>
        <li>
          Створи організацію з логотипом → зʼявиться в каруселі на
          «Організації».
        </li>
        <li>
          У кабінеті подій додай подію + афішу → зʼявиться на головній
          «Події».
        </li>
      </ol>

      <ul className="panel-list">
        <li>
          <div className="panel-row">
            <div className="panel-row__main">
              <h2 className="panel-row__title">Організації</h2>
              <p className="panel-row__meta">
                Створення, редагування, логотип, кабінет подій
              </p>
            </div>
            <div className="panel-row__actions">
              <Link className="orgs-btn" to="/admin/organizations">
                Керувати
              </Link>
            </div>
          </div>
        </li>
        <li>
          <div className="panel-row">
            <div className="panel-row__main">
              <h2 className="panel-row__title">Усі події</h2>
              <p className="panel-row__meta">
                Огляд опублікованих подій і швидкий перехід до кабінету
              </p>
            </div>
            <div className="panel-row__actions">
              <Link className="orgs-btn" to="/admin/events">
                Відкрити
              </Link>
            </div>
          </div>
        </li>
        <li>
          <div className="panel-row">
            <div className="panel-row__main">
              <h2 className="panel-row__title">Публічний сайт</h2>
              <p className="panel-row__meta">Перевірити, як бачать гості</p>
            </div>
            <div className="panel-row__actions">
              <Link className="orgs-btn" to="/">
                Головна
              </Link>
              <Link className="orgs-btn" to="/organizations">
                Організації
              </Link>
            </div>
          </div>
        </li>
      </ul>
    </section>
  )
}

export default AdminHomePage

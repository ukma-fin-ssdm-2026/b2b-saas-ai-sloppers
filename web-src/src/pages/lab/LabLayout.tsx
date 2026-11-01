import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import '../OrganizationsPage.css'
import '../panel.css'

function LabLayout() {
  const { session, logout } = useAuth()

  return (
    <main className="panel-page">
      <div className="panel-page__inner">
        <p className="panel-page__eyebrow">Lab · {session?.username}</p>
        <h1 className="panel-page__title">Секретна панель моків</h1>
        <p className="panel-banner panel-banner--lab">
          Дані живуть лише в пам’яті браузера. Бекенд і БД не чіпаються. Після
          reload сторінки seed відновиться (поки store в module scope — seed
          лишається до повного refresh вкладки).
        </p>
        <nav className="panel-nav" aria-label="Lab навігація">
          <NavLink to="/lab" end>
            Огляд
          </NavLink>
          <NavLink to="/lab/playground">Playground</NavLink>
          <NavLink to="/admin">Адмін (live API)</NavLink>
          <button type="button" className="orgs-btn" onClick={logout}>
            Вийти
          </button>
        </nav>
        <Outlet />
      </div>
    </main>
  )
}

export default LabLayout

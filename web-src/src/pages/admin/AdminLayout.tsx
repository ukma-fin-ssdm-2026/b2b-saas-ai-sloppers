import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import '../OrganizationsPage.css'
import '../panel.css'

function AdminLayout() {
  const { session, logout } = useAuth()

  return (
    <main className="panel-page">
      <div className="panel-page__inner">
        <p className="panel-page__eyebrow">Адмін · {session?.username}</p>
        <h1 className="panel-page__title">Панель керування</h1>
        <p className="panel-banner">
          Живі виклики до `/api/v1`. Якщо БД порожня — створюй дані тут або
          тестуй сценарії в <NavLink to="/lab">/lab</NavLink> на моках.
        </p>
        <nav className="panel-nav" aria-label="Адмін навігація">
          <NavLink to="/admin" end>
            Огляд
          </NavLink>
          <NavLink to="/admin/organizations">Організації</NavLink>
          <button type="button" className="orgs-btn" onClick={logout}>
            Вийти
          </button>
        </nav>
        <Outlet />
      </div>
    </main>
  )
}

export default AdminLayout

import { NavLink, Outlet } from 'react-router-dom'
import '../OrganizationsPage.css'
import '../panel.css'

function AdminLayout() {
  return (
    <main className="panel-page">
      <div className="panel-page__inner panel-page__inner--wide">
        <p className="panel-page__eyebrow">Адмін</p>
        <h1 className="panel-page__title">Панель керування</h1>
        <nav className="panel-nav" aria-label="Адмін навігація">
          <NavLink to="/admin" end>
            Огляд
          </NavLink>
          <NavLink to="/admin/organizations">Організації</NavLink>
          <NavLink to="/admin/events">Усі події</NavLink>
          <NavLink to="/admin/moderation">Модерація</NavLink>
          <NavLink to="/">Сайт →</NavLink>
        </nav>
        <Outlet />
      </div>
    </main>
  )
}

export default AdminLayout

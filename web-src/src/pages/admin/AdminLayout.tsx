import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { confirmLogout } from '../../auth/confirmLogout'
import '../OrganizationsPage.css'
import '../panel.css'

function AdminLayout() {
  const { session, logout, isDemo } = useAuth()

  const onLogout = () => {
    if (confirmLogout()) logout()
  }

  return (
    <main className="panel-page">
      <div className="panel-page__inner panel-page__inner--wide">
        <p className="panel-page__eyebrow">
          Адмін · {session?.username}
          {isDemo ? ' · демо' : ' · JWT'}
        </p>
        <h1 className="panel-page__title">Панель керування</h1>
        {isDemo ? (
          <p className="panel-banner">
            Демо-сесія не пише в API. Увійдіть як{' '}
            <code>@ukma.edu.ua</code> з Global Admin, щоб створювати дані для
            головної.
          </p>
        ) : (
          <p className="panel-banner">
            Створюй організації й події з фото — вони зʼявляться на головній
            («Події») та в каруселі «Організації».
          </p>
        )}
        <nav className="panel-nav" aria-label="Адмін навігація">
          <NavLink to="/admin" end>
            Огляд
          </NavLink>
          <NavLink to="/admin/organizations">Організації</NavLink>
          <NavLink to="/admin/events">Усі події</NavLink>
          <NavLink to="/">Сайт →</NavLink>
          <button type="button" className="orgs-btn" onClick={onLogout}>
            Вийти
          </button>
        </nav>
        <Outlet />
      </div>
    </main>
  )
}

export default AdminLayout

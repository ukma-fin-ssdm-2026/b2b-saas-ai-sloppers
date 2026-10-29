import { Link, NavLink } from 'react-router-dom'
import './AppHeader.css'

function AppHeader() {
  return (
    <header className="app-header">
      <div className="app-header__inner">
        <Link to="/organizations" className="app-header__brand">
          Могилянка
        </Link>
        <nav className="app-header__nav" aria-label="Основна навігація">
          <NavLink
            to="/organizations"
            className={({ isActive }) =>
              `app-header__link${isActive ? ' app-header__link--active' : ''}`
            }
          >
            Організації
          </NavLink>
          <NavLink
            to="/events"
            className={({ isActive }) =>
              `app-header__link${isActive ? ' app-header__link--active' : ''}`
            }
          >
            Події
          </NavLink>
        </nav>
      </div>
    </header>
  )
}

export default AppHeader

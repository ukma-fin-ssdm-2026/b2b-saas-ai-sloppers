import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { confirmLogout } from '../auth/confirmLogout'
import './AppHeader.css'

function AppHeader() {
  const { isAuthenticated, logout } = useAuth()

  const onLogout = () => {
    if (confirmLogout()) logout()
  }

  return (
    <header className="app-header">
      <div className="app-header__inner">
        <Link to="/" className="app-header__brand">
          Могилянка
        </Link>
        <nav className="app-header__nav" aria-label="Основна навігація">
          <NavLink
            to="/"
            end
            className={({ isActive }) =>
              `app-header__link${isActive ? ' app-header__link--active' : ''}`
            }
          >
            Події
          </NavLink>
          <NavLink
            to="/organizations"
            className={({ isActive }) =>
              `app-header__link${isActive ? ' app-header__link--active' : ''}`
            }
          >
            Організації
          </NavLink>
          {isAuthenticated ? (
            <>
              <NavLink
                to="/admin"
                className={({ isActive }) =>
                  `app-header__link${isActive ? ' app-header__link--active' : ''}`
                }
              >
                Адмін
              </NavLink>
              <NavLink
                to="/lab"
                className={({ isActive }) =>
                  `app-header__link${isActive ? ' app-header__link--active' : ''}`
                }
              >
                Lab
              </NavLink>
              <button
                type="button"
                className="app-header__link app-header__button"
                onClick={onLogout}
              >
                Вийти
              </button>
            </>
          ) : (
            <NavLink
              to="/login"
              className={({ isActive }) =>
                `app-header__link${isActive ? ' app-header__link--active' : ''}`
              }
            >
              Вхід
            </NavLink>
          )}
        </nav>
      </div>
    </header>
  )
}

export default AppHeader

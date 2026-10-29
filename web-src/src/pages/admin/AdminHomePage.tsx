import { Link } from 'react-router-dom'

function AdminHomePage() {
  return (
    <section>
      <p className="panel-page__lead">
        Тут можна керувати організаціями та модерувати їхні події проти
        реального бекенду (Bearer демо-токена підставляється автоматично).
      </p>
      <ul className="panel-list">
        <li>
          <div className="panel-row">
            <div className="panel-row__main">
              <h2 className="panel-row__title">Організації</h2>
              <p className="panel-row__meta">
                CRUD, logo, autoPublish → далі кабінет подій організації
              </p>
            </div>
            <div className="panel-row__actions">
              <Link className="orgs-btn" to="/admin/organizations">
                Відкрити
              </Link>
            </div>
          </div>
        </li>
        <li>
          <div className="panel-row">
            <div className="panel-row__main">
              <h2 className="panel-row__title">Lab (моки)</h2>
              <p className="panel-row__meta">
                Тест UI без БД — окрема секретна панель
              </p>
            </div>
            <div className="panel-row__actions">
              <Link className="orgs-btn" to="/lab">
                Відкрити lab
              </Link>
            </div>
          </div>
        </li>
      </ul>
    </section>
  )
}

export default AdminHomePage

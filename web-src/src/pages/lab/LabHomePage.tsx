import { Link } from 'react-router-dom'

function LabHomePage() {
  return (
    <section>
      <p className="panel-page__lead">
        Playground дає сценарії: створити організацію, додати scraped-подію в
        PENDING, approve/reject/unpublish, скинути моки — без жодного запиту
        на сервер.
      </p>
      <div className="panel-actions">
        <Link className="orgs-btn" to="/lab/playground">
          Відкрити playground
        </Link>
      </div>
    </section>
  )
}

export default LabHomePage

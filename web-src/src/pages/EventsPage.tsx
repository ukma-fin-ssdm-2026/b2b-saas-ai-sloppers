import { Link } from 'react-router-dom'

function EventsPage() {
  return (
    <main className="orgs-page">
      <div className="orgs-page__inner">
        <p className="orgs-page__eyebrow">Могилянка</p>
        <h1 className="orgs-page__title">Події</h1>
        <p className="orgs-page__status">Каталог подій: наступний етап.</p>
        <p className="orgs-page__status">
          <Link to="/organizations">Назад до каталогу організацій</Link>
        </p>
      </div>
    </main>
  )
}

export default EventsPage

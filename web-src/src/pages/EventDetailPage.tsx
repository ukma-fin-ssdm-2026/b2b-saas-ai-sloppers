import { Link, useParams } from 'react-router-dom'

function EventDetailPage() {
  const { eventId } = useParams<{ eventId: string }>()

  return (
    <main className="orgs-page">
      <div className="orgs-page__inner">
        <p className="orgs-page__eyebrow">Подія</p>
        <h1 className="orgs-page__title">Деталі</h1>
        <p className="orgs-page__status">
          Сторінка в розробці{eventId ? ` (id: ${eventId})` : ''}.
        </p>
        <p className="orgs-page__status">
          <Link to="/events">До каталогу подій</Link>
        </p>
      </div>
    </main>
  )
}

export default EventDetailPage

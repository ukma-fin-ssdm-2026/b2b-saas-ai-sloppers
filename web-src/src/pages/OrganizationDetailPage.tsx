import { Link, useParams } from 'react-router-dom'

function OrganizationDetailPage() {
  const { organizationId } = useParams<{ organizationId: string }>()

  return (
    <main className="orgs-page">
      <div className="orgs-page__inner">
        <p className="orgs-page__eyebrow">Організація</p>
        <h1 className="orgs-page__title">Профіль</h1>
        <p className="orgs-page__status">
          Сторінка в розробці{organizationId ? ` (id: ${organizationId})` : ''}.
        </p>
        <p className="orgs-page__status">
          <Link to="/organizations">До каталогу організацій</Link>
        </p>
      </div>
    </main>
  )
}

export default OrganizationDetailPage

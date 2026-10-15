import { useEffect, useState } from 'react'
import { ApiError, listOrganizations } from '../api/organizations'
import type { Organization } from '../types/organization'
import './OrganizationsPage.css'

function OrganizationsPage() {
  const [organizations, setOrganizations] = useState<Organization[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)

      try {
        const page = await listOrganizations({ limit: 20, offset: 0 })
        if (!cancelled) {
          setOrganizations(page.data)
        }
      } catch (err) {
        if (!cancelled) {
          const message =
            err instanceof ApiError
              ? `Failed to load (${err.status})`
              : 'Failed to load organizations'
          setError(message)
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void load()

    return () => {
      cancelled = true
    }
  }, [])

  return (
    <main className="orgs-page">
      <div className="orgs-page__inner">
        <p className="orgs-page__eyebrow">Могилянка</p>
        <h1 className="orgs-page__title">Організації</h1>

        {loading && <p className="orgs-page__status">Завантаження…</p>}

        {!loading && error && (
          <p
            className="orgs-page__status orgs-page__status--error"
            role="alert"
          >
            {error}
          </p>
        )}

        {!loading && !error && organizations.length === 0 && (
          <p className="orgs-page__status">Поки немає організацій.</p>
        )}

        {!loading && !error && organizations.length > 0 && (
          <ul className="orgs-list">
            {organizations.map((org) => (
              <li key={org.id} className="orgs-card">
                <span className="orgs-card__bracket" aria-hidden="true">
                  (
                </span>
                <div className="orgs-card__body">
                  <h2 className="orgs-card__name">{org.name}</h2>
                  <p className="orgs-card__slug">{org.slug}</p>
                  {org.description ? (
                    <p className="orgs-card__desc">{org.description}</p>
                  ) : null}
                </div>
                <span className="orgs-card__bracket" aria-hidden="true">
                  )
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  )
}

export default OrganizationsPage

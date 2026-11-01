import { useSyncExternalStore, type FormEvent } from 'react'
import { mockStore } from '../../mock/store'
import type { EventStatus } from '../../types/event'
import { formatEventDateTime } from '../../utils/datetime'

function useMockVersion(): number {
  return useSyncExternalStore(
    (onStoreChange) => mockStore.subscribe(onStoreChange),
    () => mockStore.getVersion(),
  )
}

function LabPlaygroundPage() {
  useMockVersion()
  const orgs = mockStore.listOrganizations(undefined, 100, 0).data

  const onCreateOrg = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const name = String(form.get('name') ?? '').trim()
    const slug = String(form.get('slug') ?? '').trim()
    try {
      mockStore.createOrganization({
        name,
        slug,
        description: String(form.get('description') ?? '').trim() || undefined,
      })
      event.currentTarget.reset()
    } catch (err) {
      window.alert(err instanceof Error ? err.message : 'Помилка')
    }
  }

  const onCreateEvent = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const organizationId = String(form.get('organizationId') ?? '')
    const title = String(form.get('title') ?? '').trim()
    const startsAtLocal = String(form.get('startsAt') ?? '')
    try {
      mockStore.createEvent(organizationId, {
        title,
        startsAt: new Date(startsAtLocal).toISOString(),
        location: String(form.get('location') ?? '').trim() || undefined,
      })
      event.currentTarget.reset()
    } catch (err) {
      window.alert(err instanceof Error ? err.message : 'Помилка')
    }
  }

  return (
    <section>
      <div className="panel-actions" style={{ marginBottom: '1.5rem' }}>
        <button
          type="button"
          className="orgs-btn"
          onClick={() => mockStore.reset()}
        >
          Скинути моки до seed
        </button>
      </div>

      <h2 className="panel-row__title">Організації (mock)</h2>
      <form className="panel-form" onSubmit={onCreateOrg}>
        <div className="panel-field">
          <label htmlFor="lab-name">Назва</label>
          <input id="lab-name" name="name" required minLength={3} />
        </div>
        <div className="panel-field">
          <label htmlFor="lab-slug">Slug</label>
          <input
            id="lab-slug"
            name="slug"
            required
            minLength={3}
            pattern="[a-z0-9]+(-[a-z0-9]+)*"
          />
        </div>
        <div className="panel-field">
          <label htmlFor="lab-desc">Опис</label>
          <textarea id="lab-desc" name="description" />
        </div>
        <button type="submit" className="orgs-btn">
          Створити org
        </button>
      </form>

      <ul className="panel-list">
        {orgs.map((org) => {
          const events = mockStore.listOrgEvents(org.id, { limit: 50 }).data
          return (
            <li key={org.id}>
              <div className="panel-row">
                <div className="panel-row__main">
                  <h3 className="panel-row__title">{org.name}</h3>
                  <p className="panel-row__meta">
                    {org.slug}
                    {org.autoPublish ? ' · autoPublish' : ''}
                  </p>
                </div>
                <div className="panel-row__actions">
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() =>
                      mockStore.updateOrganization(org.id, {
                        autoPublish: !org.autoPublish,
                      })
                    }
                  >
                    Toggle autoPublish
                  </button>
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() =>
                      mockStore.addPendingScraped(
                        org.id,
                        `Scraped post ${new Date().toLocaleTimeString('uk-UA')}`,
                      )
                    }
                  >
                    + PENDING scraped
                  </button>
                  <button
                    type="button"
                    className="orgs-btn"
                    onClick={() => {
                      if (window.confirm('Видалити mock org?')) {
                        mockStore.deleteOrganization(org.id)
                      }
                    }}
                  >
                    Delete
                  </button>
                </div>
              </div>

              <ul className="panel-list" style={{ marginLeft: '0.5rem' }}>
                {events.map((ev) => (
                  <li key={ev.id}>
                    <div className="panel-row">
                      <div className="panel-row__main">
                        <p className="panel-row__title">
                          <span className="panel-badge">{ev.status}</span>
                          {ev.title}
                        </p>
                        <p className="panel-row__meta">
                          {formatEventDateTime(ev.startsAt)}
                          {ev.verified ? '' : ' · unverified'}
                          {ev.scraped ? ' · scraped' : ''}
                        </p>
                      </div>
                      <div className="panel-row__actions">
                        <button
                          type="button"
                          className="orgs-btn"
                          onClick={() => {
                            try {
                              mockStore.approveEvent(ev.id)
                            } catch (err) {
                              window.alert(
                                err instanceof Error ? err.message : 'Помилка',
                              )
                            }
                          }}
                        >
                          approve
                        </button>
                        <button
                          type="button"
                          className="orgs-btn"
                          onClick={() => {
                            try {
                              mockStore.rejectEvent(ev.id)
                            } catch (err) {
                              window.alert(
                                err instanceof Error ? err.message : 'Помилка',
                              )
                            }
                          }}
                        >
                          reject
                        </button>
                        <button
                          type="button"
                          className="orgs-btn"
                          onClick={() => {
                            try {
                              mockStore.unpublishEvent(ev.id)
                            } catch (err) {
                              window.alert(
                                err instanceof Error ? err.message : 'Помилка',
                              )
                            }
                          }}
                        >
                          unpublish
                        </button>
                        <button
                          type="button"
                          className="orgs-btn"
                          onClick={() => {
                            const next = window.prompt('Нова назва', ev.title)
                            if (!next) return
                            mockStore.updateEvent(ev.id, { title: next })
                          }}
                        >
                          rename
                        </button>
                      </div>
                    </div>
                  </li>
                ))}
                {events.length === 0 ? (
                  <li>
                    <p className="panel-row__meta">Немає подій</p>
                  </li>
                ) : null}
              </ul>
            </li>
          )
        })}
      </ul>

      <h2 className="panel-row__title" style={{ marginTop: '2rem' }}>
        Створити PUBLISHED подію
      </h2>
      <form className="panel-form" onSubmit={onCreateEvent}>
        <div className="panel-field">
          <label htmlFor="lab-org">Організація</label>
          <select id="lab-org" name="organizationId" required>
            {orgs.map((org) => (
              <option key={org.id} value={org.id}>
                {org.name}
              </option>
            ))}
          </select>
        </div>
        <div className="panel-field">
          <label htmlFor="lab-ev-title">Назва</label>
          <input id="lab-ev-title" name="title" required minLength={3} />
        </div>
        <div className="panel-field">
          <label htmlFor="lab-ev-start">Початок</label>
          <input
            id="lab-ev-start"
            name="startsAt"
            type="datetime-local"
            required
          />
        </div>
        <div className="panel-field">
          <label htmlFor="lab-ev-loc">Локація</label>
          <input id="lab-ev-loc" name="location" />
        </div>
        <button type="submit" className="orgs-btn" disabled={orgs.length === 0}>
          Створити подію
        </button>
      </form>

      <p className="panel-row__meta" style={{ marginTop: '1rem' }}>
        Статуси:{' '}
        {(
          ['PENDING', 'PUBLISHED', 'REJECTED', 'UNPUBLISHED'] as EventStatus[]
        ).join(', ')}
      </p>
    </section>
  )
}

export default LabPlaygroundPage

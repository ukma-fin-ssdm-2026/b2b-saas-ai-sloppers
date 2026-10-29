import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, listOrganizations } from '../api/organizations'
import type { Organization } from '../types/organization'
import './OrganizationsPage.css'

const PAGE_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300

function OrganizationsPage() {
  const [searchInput, setSearchInput] = useState('')
  const [query, setQuery] = useState('')
  const [organizations, setOrganizations] = useState<Organization[]>([])
  const [hasMore, setHasMore] = useState(false)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const requestIdRef = useRef(0)

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      setQuery(searchInput.trim())
    }, SEARCH_DEBOUNCE_MS)

    return () => window.clearTimeout(timeout)
  }, [searchInput])

  useEffect(() => {
    const requestId = ++requestIdRef.current
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      setOrganizations([])
      setHasMore(false)
      setTotal(0)

      try {
        const page = await listOrganizations({
          limit: PAGE_SIZE,
          offset: 0,
          q: query || undefined,
        })
        if (cancelled || requestId !== requestIdRef.current) return

        setOrganizations(page.data)
        setHasMore(page.pagination.hasMore)
        setTotal(page.pagination.total)
      } catch (err) {
        if (cancelled || requestId !== requestIdRef.current) return

        const message =
          err instanceof ApiError
            ? `Не вдалося завантажити (${err.status})`
            : 'Не вдалося завантажити організації'
        setError(message)
      } finally {
        if (!cancelled && requestId === requestIdRef.current) {
          setLoading(false)
        }
      }
    }

    void load()

    return () => {
      cancelled = true
    }
  }, [query, reloadToken])

  const loadMore = useCallback(async () => {
    if (loading || loadingMore || !hasMore) return

    const requestId = ++requestIdRef.current
    setLoadingMore(true)
    setError(null)

    try {
      const page = await listOrganizations({
        limit: PAGE_SIZE,
        offset: organizations.length,
        q: query || undefined,
      })
      if (requestId !== requestIdRef.current) return

      setOrganizations((prev) => [...prev, ...page.data])
      setHasMore(page.pagination.hasMore)
      setTotal(page.pagination.total)
    } catch (err) {
      if (requestId !== requestIdRef.current) return

      const message =
        err instanceof ApiError
          ? `Не вдалося довантажити (${err.status})`
          : 'Не вдалося довантажити організації'
      setError(message)
    } finally {
      if (requestId === requestIdRef.current) {
        setLoadingMore(false)
      }
    }
  }, [hasMore, loading, loadingMore, organizations.length, query])

  const retry = useCallback(() => {
    setReloadToken((token) => token + 1)
  }, [])

  const showEmpty =
    !loading && !error && organizations.length === 0 && !loadingMore
  const showList = organizations.length > 0

  return (
    <main className="orgs-page">
      <div className="orgs-page__inner">
        <p className="orgs-page__eyebrow">Могилянка</p>
        <h1 className="orgs-page__title">Організації</h1>

        <div className="orgs-page__toolbar">
          <label className="orgs-search">
            <span className="orgs-search__label">Пошук</span>
            <input
              className="orgs-search__input"
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Назва або slug…"
              autoComplete="off"
              spellCheck={false}
            />
          </label>
          {!loading && !error && total > 0 ? (
            <p className="orgs-page__meta" aria-live="polite">
              Знайдено: {total}
            </p>
          ) : null}
        </div>

        {loading && <p className="orgs-page__status">Завантаження…</p>}

        {!loading && error && organizations.length === 0 && (
          <div className="orgs-page__status-block" role="alert">
            <p className="orgs-page__status orgs-page__status--error">{error}</p>
            <button type="button" className="orgs-btn" onClick={retry}>
              Спробувати знову
            </button>
          </div>
        )}

        {showEmpty && (
          <p className="orgs-page__status">
            {query
              ? `Нічого не знайдено за запитом «${query}».`
              : 'Поки немає організацій.'}
          </p>
        )}

        {showList && (
          <>
            <ul className="orgs-list">
              {organizations.map((org) => (
                <li key={org.id}>
                  <Link
                    to={`/organizations/${org.id}`}
                    className="orgs-card"
                  >
                    <span className="orgs-card__bracket" aria-hidden="true">
                      (
                    </span>
                    <div className="orgs-card__media" aria-hidden={!org.logoUrl}>
                      {org.logoUrl ? (
                        <img
                          className="orgs-card__logo"
                          src={org.logoUrl}
                          alt=""
                          loading="lazy"
                        />
                      ) : (
                        <span className="orgs-card__logo-fallback">
                          {org.name.charAt(0).toUpperCase()}
                        </span>
                      )}
                    </div>
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
                  </Link>
                </li>
              ))}
            </ul>

            {error && (
              <div className="orgs-page__status-block" role="alert">
                <p className="orgs-page__status orgs-page__status--error">
                  {error}
                </p>
                <button type="button" className="orgs-btn" onClick={loadMore}>
                  Спробувати знову
                </button>
              </div>
            )}

            {!error && hasMore && (
              <div className="orgs-page__more">
                <button
                  type="button"
                  className="orgs-btn"
                  onClick={() => void loadMore()}
                  disabled={loadingMore}
                >
                  {loadingMore ? 'Завантаження…' : 'Показати ще'}
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </main>
  )
}

export default OrganizationsPage

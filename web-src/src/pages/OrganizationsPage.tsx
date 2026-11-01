import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, listOrganizations } from '../api/organizations'
import CircularCarousel, {
  type CircularCarouselItem,
} from '../components/CircularCarousel'
import type { Organization } from '../types/organization'
import './OrganizationsPage.css'

const PAGE_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300

function logoPlaceholder(name: string): string {
  const letter = (name.charAt(0) || '?').toUpperCase()
  const svg = `
<svg xmlns="http://www.w3.org/2000/svg" width="900" height="900" viewBox="0 0 900 900">
  <rect width="900" height="900" fill="#161616"/>
  <rect x="48" y="48" width="804" height="804" fill="none" stroke="#2f2f2f" stroke-width="2"/>
  <text x="450" y="510" text-anchor="middle" fill="#cfcfcf"
    font-family="Georgia, Times New Roman, serif" font-size="280">${letter}</text>
</svg>`.trim()
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

function toCarouselItem(org: Organization): CircularCarouselItem {
  return {
    src: org.logoUrl || logoPlaceholder(org.name),
    alt: `Логотип ${org.name}`,
    title: org.name,
  }
}

function OrganizationsPage() {
  const navigate = useNavigate()
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
          ? `Не вдалося завантажити (${err.status})`
          : 'Не вдалося завантажити організації'
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

  const carouselItems = useMemo(
    () => organizations.map(toCarouselItem),
    [organizations],
  )

  const showEmpty =
    !loading && !error && organizations.length === 0 && !loadingMore
  const showCarousel = organizations.length > 0

  return (
    <main className="orgs-page">
      <div className="orgs-page__inner">
        <div className="orgs-page__heading">
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

        {showCarousel && (
          <>
            <div className="orgs-page__carousel">
              <CircularCarousel
                items={carouselItems}
                preset="cylinder"
                intro="rise"
                cardWidth={220}
                aspectRatio={1}
                speed={14}
                captions={false}
                gap={25}
                tilt={-5}
                curve={1}
                perspective={2500}
                autoplay="drift"
                interval={3}
                direction="left"
                momentum={0.6}
                snap
                pauseOnHover
                focusOnClick
                draggable
                parallax={0.3}
                stretch={0.5}
                fadeColor="#0a0a0a"
                depthFade={0.55}
                innerShade={0.6}
                cornerRadius={12}
                onItemClick={(_item, index) => {
                  const org = organizations.at(index)
                  if (org) navigate(`/organizations/${org.id}`)
                }}
              />
            </div>

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

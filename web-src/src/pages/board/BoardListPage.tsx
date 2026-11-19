import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  listAdverts,
  listMyAdverts,
  listRepliedAdverts,
} from '../../api/adverts'
import { errorMessage } from '../../api/errorMessage'
import type { Advert, AdvertType } from '../../types/advert'
import {
  ADVERT_STATUS_LABELS,
  ADVERT_TYPE_LABELS,
  REPLY_STATUS_LABELS,
} from '../../types/advert'
import '../OrganizationsPage.css'
import '../panel.css'
import './board.css'

export type BoardListScope = 'open' | 'mine' | 'replied'

type Props = { scope: BoardListScope }

function formatDeadline(iso: string): string {
  try {
    return new Date(iso).toLocaleString('uk-UA', {
      dateStyle: 'medium',
      timeStyle: 'short',
    })
  } catch {
    return iso
  }
}

function BoardListPage({ scope }: Props) {
  const [items, setItems] = useState<Advert[]>([])
  const [type, setType] = useState<AdvertType | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [offset, setOffset] = useState(0)
  const limit = 20

  const load = useCallback(
    async (nextOffset: number, append: boolean) => {
      setLoading(true)
      setError(null)
      try {
        const page =
          scope === 'mine'
            ? await listMyAdverts({ offset: nextOffset, limit })
            : scope === 'replied'
              ? await listRepliedAdverts({ offset: nextOffset, limit })
              : await listAdverts({
                  offset: nextOffset,
                  limit,
                  type: type || undefined,
                })
        setItems((prev) => (append ? [...prev, ...page.data] : page.data))
        setHasMore(page.pagination.hasMore)
        setOffset(nextOffset + page.data.length)
      } catch (err) {
        setError(errorMessage(err, 'Не вдалося завантажити оголошення'))
      } finally {
        setLoading(false)
      }
    },
    [scope, type],
  )

  useEffect(() => {
    void load(0, false)
  }, [load])

  return (
    <section className="board-list">
      {scope === 'open' ? (
        <div className="board-filters">
          <label className="board-filters__field">
            <span>Тип</span>
            <select
              value={type}
              onChange={(e) => setType(e.target.value as AdvertType | '')}
            >
              <option value="">Усі</option>
              <option value="TEAM">{ADVERT_TYPE_LABELS.TEAM}</option>
              <option value="LEISURE">{ADVERT_TYPE_LABELS.LEISURE}</option>
            </select>
          </label>
        </div>
      ) : null}

      {error ? (
        <p className="panel-error" role="alert">
          {error}
        </p>
      ) : null}
      {loading && items.length === 0 ? (
        <p className="panel-page__lead">Завантаження…</p>
      ) : null}
      {!loading && items.length === 0 && !error ? (
        <p className="panel-page__lead">Поки немає оголошень.</p>
      ) : null}

      <ul className="panel-list board-cards">
        {items.map((advert) => (
          <li key={advert.id}>
            <Link to={`/board/${advert.id}`} className="board-card">
              <div className="board-card__top">
                <span className="panel-badge">
                  {ADVERT_TYPE_LABELS[advert.type]}
                </span>
                <span className="panel-badge">
                  {ADVERT_STATUS_LABELS[advert.status]}
                </span>
                {advert.myReplyStatus ? (
                  <span className="panel-badge">
                    {REPLY_STATUS_LABELS[advert.myReplyStatus]}
                  </span>
                ) : null}
              </div>
              <h2 className="board-card__title">{advert.title}</h2>
              <p className="board-card__meta">
                {advert.authorName ?? 'Без імені'} · {advert.slots} місць · до{' '}
                {formatDeadline(advert.deadline)}
              </p>
              <p className="board-card__desc">{advert.description}</p>
            </Link>
          </li>
        ))}
      </ul>

      {hasMore ? (
        <div className="panel-actions">
          <button
            type="button"
            className="orgs-btn"
            disabled={loading}
            onClick={() => void load(offset, true)}
          >
            {loading ? 'Завантаження…' : 'Ще'}
          </button>
        </div>
      ) : null}
    </section>
  )
}

export default BoardListPage

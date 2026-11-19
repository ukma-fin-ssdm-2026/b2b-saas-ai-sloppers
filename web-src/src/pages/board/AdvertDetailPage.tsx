import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  closeAdvert,
  confirmAdvertReply,
  deleteAdvert,
  getAdvert,
  listAdvertReplies,
  rejectAdvertReply,
  replyToAdvert,
  withdrawAdvertReply,
} from '../../api/adverts'
import { errorMessage } from '../../api/errorMessage'
import type { Advert, AdvertReply } from '../../types/advert'
import {
  ADVERT_STATUS_LABELS,
  ADVERT_TYPE_LABELS,
  REPLY_STATUS_LABELS,
} from '../../types/advert'
import '../OrganizationsPage.css'
import '../panel.css'
import './board.css'

function formatWhen(iso: string): string {
  try {
    return new Date(iso).toLocaleString('uk-UA', {
      dateStyle: 'medium',
      timeStyle: 'short',
    })
  } catch {
    return iso
  }
}

function AdvertDetailPage() {
  const { advertId = '' } = useParams()
  const navigate = useNavigate()
  const [advert, setAdvert] = useState<Advert | null>(null)
  const [replies, setReplies] = useState<AdvertReply[]>([])
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [ok, setOk] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    if (!advertId) return
    setLoading(true)
    setError(null)
    try {
      const next = await getAdvert(advertId)
      setAdvert(next)
      if (next.own) {
        setReplies(await listAdvertReplies(advertId))
      } else {
        setReplies([])
      }
    } catch (err) {
      setError(errorMessage(err, 'Не вдалося завантажити оголошення'))
      setAdvert(null)
    } finally {
      setLoading(false)
    }
  }, [advertId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const run = async (action: () => Promise<void>, success: string) => {
    setBusy(true)
    setError(null)
    setOk(null)
    try {
      await action()
      setOk(success)
      await refresh()
    } catch (err) {
      setError(errorMessage(err, 'Дію не виконано'))
    } finally {
      setBusy(false)
    }
  }

  const onReply = async (event: FormEvent) => {
    event.preventDefault()
    await run(async () => {
      await replyToAdvert(advertId, message)
      setMessage('')
    }, 'Відгук надіслано')
  }

  if (loading) {
    return <p className="panel-page__lead">Завантаження…</p>
  }
  if (!advert) {
    return (
      <div>
        {error ? <p className="panel-error">{error}</p> : null}
        <Link to="/board" className="orgs-btn">
          До дошки
        </Link>
      </div>
    )
  }

  const canReply =
    !advert.own &&
    advert.status === 'ACTIVE' &&
    (!advert.myReplyStatus || advert.myReplyStatus === 'WITHDRAWN')

  const canWithdraw =
    advert.myReplyStatus === 'PENDING' && Boolean(advert.myReplyId)

  return (
    <article className="board-detail">
      <p className="panel-page__eyebrow">
        <Link to="/board">Дошка</Link> · {ADVERT_TYPE_LABELS[advert.type]}
      </p>
      <h2 className="board-section-title">{advert.title}</h2>
      <div className="board-card__top">
        <span className="panel-badge">
          {ADVERT_STATUS_LABELS[advert.status]}
        </span>
        {advert.own ? <span className="panel-badge">Ваше</span> : null}
        {advert.myReplyStatus ? (
          <span className="panel-badge">
            {REPLY_STATUS_LABELS[advert.myReplyStatus]}
          </span>
        ) : null}
      </div>
      <p className="board-card__meta">
        {advert.authorName ?? 'Без імені'} · {advert.slots} місць · до{' '}
        {formatWhen(advert.deadline)} · створено {formatWhen(advert.createdAt)}
      </p>
      <p className="board-detail__body">{advert.description}</p>

      {advert.contact ? (
        <p className="panel-ok">Контакт: {advert.contact}</p>
      ) : (
        <p className="panel-hint">
          Контакт з’явиться після підтвердження вашого відгуку автором.
        </p>
      )}

      {error ? <p className="panel-error">{error}</p> : null}
      {ok ? <p className="panel-ok">{ok}</p> : null}

      {advert.own ? (
        <div className="panel-actions">
          {advert.status === 'ACTIVE' ? (
            <button
              type="button"
              className="orgs-btn"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await closeAdvert(advertId)
                }, 'Оголошення закрито')
              }
            >
              Закрити
            </button>
          ) : null}
          <button
            type="button"
            className="orgs-btn"
            disabled={busy}
            onClick={() => {
              if (!window.confirm('Видалити оголошення?')) return
              void run(async () => {
                await deleteAdvert(advertId)
                navigate('/board/mine', { replace: true })
              }, 'Видалено')
            }}
          >
            Видалити
          </button>
        </div>
      ) : null}

      {canReply ? (
        <form className="panel-form" onSubmit={(e) => void onReply(e)}>
          <div className="panel-field">
            <label htmlFor="reply-msg">Повідомлення автору (опційно)</label>
            <textarea
              id="reply-msg"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              maxLength={500}
              rows={3}
            />
          </div>
          <div className="panel-actions">
            <button type="submit" className="orgs-btn" disabled={busy}>
              Відгукнутися
            </button>
          </div>
        </form>
      ) : null}

      {canWithdraw && advert.myReplyId ? (
        <div className="panel-actions">
          <button
            type="button"
            className="orgs-btn"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await withdrawAdvertReply(advertId, advert.myReplyId!)
              }, 'Відгук відкликано')
            }
          >
            Відкликати відгук
          </button>
        </div>
      ) : null}

      {advert.own ? (
        <section className="board-replies">
          <h3 className="board-section-title">Відгуки</h3>
          {replies.length === 0 ? (
            <p className="panel-hint">Поки немає відгуків.</p>
          ) : (
            <ul className="panel-list">
              {replies.map((reply) => (
                <li key={reply.id} className="panel-row">
                  <div className="panel-row__main">
                    <p className="panel-row__title">
                      {reply.userName ?? 'Без імені'}{' '}
                      <span className="panel-badge">
                        {REPLY_STATUS_LABELS[reply.status]}
                      </span>
                    </p>
                    <p className="panel-row__meta">
                      {formatWhen(reply.createdAt)}
                      {reply.message ? ` · ${reply.message}` : ''}
                    </p>
                  </div>
                  {reply.status === 'PENDING' ? (
                    <div className="panel-row__actions">
                      <button
                        type="button"
                        className="orgs-btn"
                        disabled={busy}
                        onClick={() =>
                          void run(async () => {
                            await confirmAdvertReply(advertId, reply.id)
                          }, 'Відгук підтверджено')
                        }
                      >
                        Підтвердити
                      </button>
                      <button
                        type="button"
                        className="orgs-btn"
                        disabled={busy}
                        onClick={() =>
                          void run(async () => {
                            await rejectAdvertReply(advertId, reply.id)
                          }, 'Відгук відхилено')
                        }
                      >
                        Відхилити
                      </button>
                    </div>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}
    </article>
  )
}

export default AdvertDetailPage

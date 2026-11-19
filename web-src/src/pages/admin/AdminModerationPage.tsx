import { useEffect, useState, type FormEvent } from 'react'
import {
  addBannedWord,
  banUser,
  dismissComplaint,
  listBannedWords,
  listBans,
  listComplaints,
  removeBannedWord,
  resolveComplaint,
  unbanUser,
} from '../../api/moderation'
import type {
  Ban,
  BannedWord,
  Complaint,
  ComplaintStatus,
} from '../../types/moderation'
import {
  COMPLAINT_CATEGORY_LABELS,
  COMPLAINT_STATUS_LABELS,
  CONTENT_TYPE_LABELS,
} from '../../types/moderation'
import { apiErrorMessage } from '../../utils/apiErrorMessage'
import { formatEventDateTime } from '../../utils/datetime'

const DAY_MS = 24 * 60 * 60 * 1000
const QUEUE_LIMIT = 50
const UUID_PATTERN =
  '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'

type StatusFilter = ComplaintStatus | 'ALL'

const BAN_TERMS = [
  { value: '1', label: '1 день' },
  { value: '7', label: '7 днів' },
  { value: '30', label: '30 днів' },
  { value: 'permanent', label: 'Безстроково' },
] as const

type BanTerm = (typeof BAN_TERMS)[number]['value']

function expiresAtFor(term: BanTerm, now = Date.now()): string | null {
  if (term === 'permanent') return null
  return new Date(now + Number(term) * DAY_MS).toISOString()
}

// ---- черга скарг (FR-057) ----

interface ComplaintQueueProps {
  onBanAuthor: (userId: string) => void
}

function ComplaintQueue({ onBanAuthor }: ComplaintQueueProps) {
  const [status, setStatus] = useState<StatusFilter>('OPEN')
  const [items, setItems] = useState<Complaint[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [notes, setNotes] = useState<Map<string, string>>(() => new Map())
  const [busyId, setBusyId] = useState<string | null>(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const page = await listComplaints({
          status: status === 'ALL' ? undefined : status,
          limit: QUEUE_LIMIT,
          offset: 0,
        })
        if (!cancelled) {
          setItems(page.data)
          setTotal(page.pagination.total)
        }
      } catch (err) {
        if (!cancelled) {
          setError(apiErrorMessage(err, 'Не вдалося завантажити скарги'))
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [status, reload])

  const setNote = (id: string, value: string) => {
    setNotes((prev) => new Map(prev).set(id, value))
  }

  const decide = async (complaint: Complaint, remove: boolean) => {
    setBusyId(complaint.id)
    setError(null)
    setMessage(null)
    try {
      const note = notes.get(complaint.id)
      if (remove) {
        await resolveComplaint(complaint.id, note)
        setMessage(
          `${CONTENT_TYPE_LABELS[complaint.targetType]} прибрано, скаргу закрито`,
        )
      } else {
        await dismissComplaint(complaint.id, note)
        setMessage('Скаргу відхилено, контент лишився')
      }
      setReload((n) => n + 1)
    } catch (err) {
      setError(apiErrorMessage(err, 'Рішення не збереглося'))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section aria-labelledby="queue-title">
      <h2 id="queue-title" className="panel-row__title">
        Черга скарг
      </h2>
      <p className="panel-page__lead">
        «Прибрати» видаляє оголошення або відгук і закриває всі інші скарги на
        нього. Відхилені скарги не рахуються в автоблокуванні.
      </p>

      <div className="panel-field">
        <label htmlFor="queue-status">Стан</label>
        <select
          id="queue-status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as StatusFilter)
            setMessage(null)
          }}
        >
          <option value="OPEN">Відкриті</option>
          <option value="RESOLVED">Контент прибрано</option>
          <option value="DISMISSED">Відхилені</option>
          <option value="ALL">Усі</option>
        </select>
      </div>

      {message ? <p className="panel-ok">{message}</p> : null}
      {error ? <p className="panel-error">{error}</p> : null}

      {loading ? (
        <p className="panel-hint">Завантаження…</p>
      ) : items.length === 0 ? (
        <p className="panel-hint">Скарг немає.</p>
      ) : (
        <>
          <p className="panel-hint">
            Показано {items.length} з {total}
          </p>
          <ul className="panel-list" aria-label="Скарги">
            {items.map((complaint) => (
              <li key={complaint.id}>
                <div className="panel-row">
                  <div className="panel-row__main">
                    <h3 className="panel-row__title">
                      <span className="panel-badge">
                        {CONTENT_TYPE_LABELS[complaint.targetType]}
                      </span>
                      <span className="panel-badge">
                        {COMPLAINT_CATEGORY_LABELS[complaint.category]}
                      </span>
                      {complaint.authorName ?? complaint.authorId}
                    </h3>
                    <p className="panel-row__meta">
                      {formatEventDateTime(complaint.createdAt)} ·{' '}
                      {COMPLAINT_STATUS_LABELS[complaint.status]}
                      {complaint.resolution ? ` · ${complaint.resolution}` : ''}
                    </p>
                    {complaint.comment ? (
                      <p className="panel-row__meta">«{complaint.comment}»</p>
                    ) : null}
                    <p className="panel-hint">
                      ID контенту: {complaint.targetId}
                    </p>
                    {complaint.status === 'OPEN' ? (
                      <div className="panel-field">
                        <label htmlFor={`note-${complaint.id}`}>
                          Коментар до рішення (необовʼязково)
                        </label>
                        <input
                          id={`note-${complaint.id}`}
                          maxLength={500}
                          value={notes.get(complaint.id) ?? ''}
                          onChange={(e) =>
                            setNote(complaint.id, e.target.value)
                          }
                        />
                      </div>
                    ) : null}
                  </div>
                  <div className="panel-row__actions">
                    {complaint.status === 'OPEN' ? (
                      <>
                        <button
                          type="button"
                          className="orgs-btn"
                          disabled={busyId === complaint.id}
                          onClick={() => void decide(complaint, true)}
                        >
                          Прибрати
                        </button>
                        <button
                          type="button"
                          className="orgs-btn"
                          disabled={busyId === complaint.id}
                          onClick={() => void decide(complaint, false)}
                        >
                          Відхилити
                        </button>
                      </>
                    ) : null}
                    <button
                      type="button"
                      className="orgs-btn"
                      onClick={() => onBanAuthor(complaint.authorId)}
                    >
                      Заблокувати автора
                    </button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}

// ---- блокування (FR-054) ----

interface BanPanelProps {
  userId: string
  onUserIdChange: (userId: string) => void
}

function BanPanel({ userId, onUserIdChange }: BanPanelProps) {
  const [reason, setReason] = useState('')
  const [term, setTerm] = useState<BanTerm>('7')
  const [history, setHistory] = useState<Ban[] | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const loadHistory = async (id: string) => {
    try {
      setHistory(await listBans(id))
    } catch (err) {
      setHistory(null)
      setError(apiErrorMessage(err, 'Не вдалося завантажити історію'))
    }
  }

  const onBan = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    setMessage(null)
    const id = userId.trim()
    try {
      const ban = await banUser(id, {
        reason: reason.trim(),
        expiresAt: expiresAtFor(term),
      })
      setMessage(
        ban.expiresAt
          ? `Заблоковано до ${formatEventDateTime(ban.expiresAt)}`
          : 'Заблоковано безстроково',
      )
      setReason('')
      await loadHistory(id)
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося заблокувати'))
    } finally {
      setSaving(false)
    }
  }

  const onUnban = async () => {
    setSaving(true)
    setError(null)
    setMessage(null)
    const id = userId.trim()
    try {
      await unbanUser(id)
      setMessage('Розблоковано')
      await loadHistory(id)
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося розблокувати'))
    } finally {
      setSaving(false)
    }
  }

  const activeBan = history?.find((b) => b.active) ?? null

  return (
    <section aria-labelledby="ban-title">
      <h2 id="ban-title" className="panel-row__title">
        Блокування
      </h2>
      <p className="panel-page__lead">
        Заблокований не може входити в захищені розділи, а його оголошення
        зникають із дошки. Global Admin заблокувати не можна.
      </p>

      <form className="panel-form" onSubmit={(e) => void onBan(e)}>
        <div className="panel-field">
          <label htmlFor="ban-user">ID користувача</label>
          <input
            id="ban-user"
            value={userId}
            onChange={(e) => {
              onUserIdChange(e.target.value)
              setHistory(null)
            }}
            required
            pattern={UUID_PATTERN}
            title="UUID користувача"
            placeholder="a1000000-0000-4000-8000-000000000001"
          />
          <span className="panel-hint">
            Найпростіше — кнопкою «Заблокувати автора» у черзі скарг.
          </span>
        </div>
        <div className="panel-grid">
          <div className="panel-field">
            <label htmlFor="ban-reason">Причина</label>
            <input
              id="ban-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              required
              maxLength={500}
              placeholder="Спам на дошці оголошень"
            />
          </div>
          <div className="panel-field">
            <label htmlFor="ban-term">Строк</label>
            <select
              id="ban-term"
              value={term}
              onChange={(e) => setTerm(e.target.value as BanTerm)}
            >
              {BAN_TERMS.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="panel-actions">
          <button type="submit" className="orgs-btn" disabled={saving}>
            {saving ? 'Зберігаємо…' : 'Заблокувати'}
          </button>
          <button
            type="button"
            className="orgs-btn"
            disabled={saving || userId.trim() === ''}
            onClick={() => void loadHistory(userId.trim())}
          >
            Історія
          </button>
          {activeBan ? (
            <button
              type="button"
              className="orgs-btn"
              disabled={saving}
              onClick={() => void onUnban()}
            >
              Розблокувати
            </button>
          ) : null}
        </div>
      </form>

      {message ? <p className="panel-ok">{message}</p> : null}
      {error ? <p className="panel-error">{error}</p> : null}

      {history !== null ? (
        history.length === 0 ? (
          <p className="panel-hint">Блокувань не було.</p>
        ) : (
          <ul className="panel-list" aria-label="Історія блокувань">
            {history.map((ban) => (
              <li key={ban.id}>
                <div className="panel-row">
                  <div className="panel-row__main">
                    <h3 className="panel-row__title">
                      <span className="panel-badge">
                        {ban.active ? 'Діє' : 'Знято'}
                      </span>
                      {ban.automatic ? (
                        <span className="panel-badge">Авто</span>
                      ) : null}
                      {ban.reason}
                    </h3>
                    <p className="panel-row__meta">
                      з {formatEventDateTime(ban.createdAt)} ·{' '}
                      {ban.expiresAt
                        ? `до ${formatEventDateTime(ban.expiresAt)}`
                        : 'безстроково'}
                    </p>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )
      ) : null}
    </section>
  )
}

// ---- автофільтр (FR-058) ----

function BannedWordsPanel() {
  const [words, setWords] = useState<BannedWord[]>([])
  const [word, setWord] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const list = await listBannedWords()
        if (!cancelled) setWords(list)
      } catch (err) {
        if (!cancelled) {
          setError(apiErrorMessage(err, 'Не вдалося завантажити список слів'))
        }
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [reload])

  const onAdd = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await addBannedWord(word)
      setWord('')
      setReload((n) => n + 1)
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося додати слово'))
    } finally {
      setSaving(false)
    }
  }

  const onRemove = async (wordId: string) => {
    setError(null)
    try {
      await removeBannedWord(wordId)
      setReload((n) => n + 1)
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося прибрати слово'))
    }
  }

  return (
    <section aria-labelledby="words-title">
      <h2 id="words-title" className="panel-row__title">
        Заборонені слова
      </h2>
      <p className="panel-page__lead">
        Оголошення й відгуки з цими словами відхиляються одразу. Порівнюються
        слова цілком: «кіт» не блокує «Нікіту».
      </p>

      <form className="panel-form" onSubmit={(e) => void onAdd(e)}>
        <div className="panel-field">
          <label htmlFor="word-new">Нове слово</label>
          <input
            id="word-new"
            value={word}
            onChange={(e) => setWord(e.target.value)}
            required
            minLength={2}
            maxLength={100}
            pattern="\S+"
            title="одне слово, без пробілів"
            placeholder="казино"
          />
        </div>
        <div className="panel-actions">
          <button type="submit" className="orgs-btn" disabled={saving}>
            Додати
          </button>
        </div>
      </form>

      {error ? <p className="panel-error">{error}</p> : null}

      {words.length === 0 ? (
        <p className="panel-hint">Список порожній.</p>
      ) : (
        <ul className="panel-list" aria-label="Заборонені слова">
          {words.map((w) => (
            <li key={w.id}>
              <div className="panel-row">
                <div className="panel-row__main">
                  <h3 className="panel-row__title">{w.word}</h3>
                </div>
                <div className="panel-row__actions">
                  <button
                    type="button"
                    className="orgs-btn"
                    aria-label={`Прибрати «${w.word}»`}
                    onClick={() => void onRemove(w.id)}
                  >
                    Прибрати
                  </button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/** Модерація для Global Admin (FR-054, FR-057, FR-058). */
function AdminModerationPage() {
  const [banUserId, setBanUserId] = useState('')

  return (
    <>
      <ComplaintQueue onBanAuthor={setBanUserId} />
      <BanPanel userId={banUserId} onUserIdChange={setBanUserId} />
      <BannedWordsPanel />
    </>
  )
}

export default AdminModerationPage

import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  closeAdvert,
  createAdvert,
  deleteAdvert,
  listAdverts,
} from '../api/adverts'
import type { Advert, AdvertType, ReplyStatus } from '../types/advert'
import { formatEventDateTime } from '../utils/datetime'
import { apiErrorMessage } from '../utils/apiErrorMessage'

const TYPE_LABEL: Record<AdvertType, string> = {
  TEAM: 'Команда',
  LEISURE: 'Дозвілля',
}

const REPLY_LABEL: Record<ReplyStatus, string> = {
  PENDING: 'ваш відгук очікує',
  CONFIRMED: 'відгук підтверджено',
  REJECTED: 'відгук відхилено',
  WITHDRAWN: 'відгук відкликано',
}

const PAGE_SIZE = 20

function BoardPage() {
  const [adverts, setAdverts] = useState<Advert[]>([])
  const [total, setTotal] = useState(0)
  const [type, setType] = useState<AdvertType | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [formOpen, setFormOpen] = useState(false)

  const [newType, setNewType] = useState<AdvertType>('TEAM')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [slots, setSlots] = useState('1')
  const [deadline, setDeadline] = useState('')
  const [contact, setContact] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const page = await listAdverts({
        type: type === '' ? undefined : type,
        limit: PAGE_SIZE,
      })
      setAdverts(page.data)
      setTotal(page.pagination.total)
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося завантажити дошку'))
    } finally {
      setLoading(false)
    }
  }, [type])

  useEffect(() => {
    void load()
  }, [load])

  async function onCreate(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await createAdvert({
        type: newType,
        title: title.trim(),
        description: description.trim(),
        slots: Number(slots),
        // datetime-local не має зсуву, тож доводимо до ISO зі зсувом браузера
        deadline: new Date(deadline).toISOString(),
        contact: contact.trim(),
      })
      setTitle('')
      setDescription('')
      setSlots('1')
      setDeadline('')
      setContact('')
      setFormOpen(false)
      setMessage('Оголошення опубліковано')
      await load()
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося створити оголошення'))
    } finally {
      setBusy(false)
    }
  }

  async function onClose(advert: Advert) {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await closeAdvert(advert.id)
      setMessage('Оголошення закрито')
      await load()
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося закрити оголошення'))
    } finally {
      setBusy(false)
    }
  }

  async function onDelete(advert: Advert) {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await deleteAdvert(advert.id)
      setMessage('Оголошення видалено')
      await load()
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося видалити оголошення'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="orgs-page">
      <h2 className="panel-row__title">Дошка оголошень</h2>
      <p className="panel-hint">
        Пошук команди на хакатон чи компанії для дозвілля. Контакт автора видно
        лише тому, чий відгук автор підтвердив.
      </p>

      <div className="panel-actions">
        <label className="panel-field">
          <span>Тип</span>
          <select
            aria-label="Тип оголошення"
            value={type}
            onChange={(e) => setType(e.target.value as AdvertType | '')}
          >
            <option value="">Усі</option>
            <option value="TEAM">Команда</option>
            <option value="LEISURE">Дозвілля</option>
          </select>
        </label>
        <button
          type="button"
          className="orgs-btn"
          onClick={() => setFormOpen((open) => !open)}
        >
          {formOpen ? 'Скасувати' : 'Нове оголошення'}
        </button>
      </div>

      {message ? <p className="panel-ok">{message}</p> : null}
      {error ? <p className="panel-error">{error}</p> : null}

      {formOpen ? (
        <form className="panel-form" onSubmit={(e) => void onCreate(e)}>
          <div className="panel-grid">
            <div className="panel-field">
              <label htmlFor="advert-type">Тип</label>
              <select
                id="advert-type"
                value={newType}
                onChange={(e) => setNewType(e.target.value as AdvertType)}
              >
                <option value="TEAM">Команда</option>
                <option value="LEISURE">Дозвілля</option>
              </select>
            </div>
            <div className="panel-field">
              <label htmlFor="advert-slots">Місць</label>
              <input
                id="advert-slots"
                type="number"
                min={1}
                max={50}
                value={slots}
                onChange={(e) => setSlots(e.target.value)}
                required
              />
            </div>
          </div>
          <div className="panel-field">
            <label htmlFor="advert-title">Заголовок</label>
            <input
              id="advert-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              minLength={3}
              maxLength={200}
              required
            />
          </div>
          <div className="panel-field">
            <label htmlFor="advert-description">Опис</label>
            <textarea
              id="advert-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={2000}
              required
            />
          </div>
          <div className="panel-grid">
            <div className="panel-field">
              <label htmlFor="advert-deadline">Дедлайн</label>
              <input
                id="advert-deadline"
                type="datetime-local"
                value={deadline}
                onChange={(e) => setDeadline(e.target.value)}
                required
              />
            </div>
            <div className="panel-field">
              <label htmlFor="advert-contact">Контакт</label>
              <input
                id="advert-contact"
                value={contact}
                onChange={(e) => setContact(e.target.value)}
                placeholder="@telegram, телефон або email"
                required
              />
            </div>
          </div>
          <div className="panel-actions">
            <button type="submit" className="orgs-btn" disabled={busy}>
              {busy ? 'Публікація…' : 'Опублікувати'}
            </button>
          </div>
        </form>
      ) : null}

      {loading ? <p className="panel-hint">Завантаження…</p> : null}

      {!loading && adverts.length === 0 ? (
        <p className="panel-hint">Активних оголошень немає</p>
      ) : null}

      <ul className="panel-list">
        {adverts.map((advert) => (
          <li className="panel-row" key={advert.id}>
            <div>
              <h3 className="panel-row__title">{advert.title}</h3>
              <p>{advert.description}</p>
              <p className="panel-hint">
                <span className="panel-badge">{TYPE_LABEL[advert.type]}</span>{' '}
                місць: {advert.slots} · до{' '}
                {formatEventDateTime(advert.deadline)} ·{' '}
                {advert.authorName ?? 'автор без імені'}
                {advert.status === 'CLOSED' ? ' · закрито' : ''}
                {advert.myReplyStatus
                  ? ` · ${REPLY_LABEL[advert.myReplyStatus]}`
                  : ''}
              </p>
              {advert.contact ? (
                <p className="panel-ok">Контакт: {advert.contact}</p>
              ) : null}
            </div>
            {advert.own ? (
              <div className="panel-actions">
                {advert.status === 'ACTIVE' ? (
                  <button
                    type="button"
                    className="orgs-btn"
                    disabled={busy}
                    onClick={() => void onClose(advert)}
                  >
                    Закрити
                  </button>
                ) : null}
                <button
                  type="button"
                  className="orgs-btn"
                  disabled={busy}
                  onClick={() => void onDelete(advert)}
                >
                  Видалити
                </button>
              </div>
            ) : null}
          </li>
        ))}
      </ul>

      {total > adverts.length ? (
        <p className="panel-hint">
          Показано {adverts.length} з {total}
        </p>
      ) : null}
    </section>
  )
}

export default BoardPage

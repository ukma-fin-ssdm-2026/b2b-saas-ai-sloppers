import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createAdvert } from '../../api/adverts'
import { errorMessage } from '../../api/errorMessage'
import type { AdvertType } from '../../types/advert'
import { ADVERT_TYPE_LABELS } from '../../types/advert'
import '../OrganizationsPage.css'
import '../panel.css'

function toLocalInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function AdvertCreatePage() {
  const navigate = useNavigate()
  const [type, setType] = useState<AdvertType>('TEAM')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [slots, setSlots] = useState(1)
  const [deadline, setDeadline] = useState(() => {
    const d = new Date()
    d.setDate(d.getDate() + 7)
    return toLocalInputValue(d)
  })
  const [contact, setContact] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const created = await createAdvert({
        type,
        title: title.trim(),
        description: description.trim(),
        slots,
        deadline: new Date(deadline).toISOString(),
        contact: contact.trim(),
      })
      void navigate(`/board/${created.id}`, { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Не вдалося створити оголошення'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section>
      <h2 className="board-section-title">Нове оголошення</h2>
      <form className="panel-form" onSubmit={(e) => void onSubmit(e)}>
        <div className="panel-field">
          <label htmlFor="adv-type">Тип</label>
          <select
            id="adv-type"
            value={type}
            onChange={(e) => setType(e.target.value as AdvertType)}
            required
          >
            <option value="TEAM">{ADVERT_TYPE_LABELS.TEAM}</option>
            <option value="LEISURE">{ADVERT_TYPE_LABELS.LEISURE}</option>
          </select>
        </div>
        <div className="panel-field">
          <label htmlFor="adv-title">Заголовок</label>
          <input
            id="adv-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            minLength={3}
            maxLength={200}
            placeholder="Шукаю бекендера на хакатон"
          />
        </div>
        <div className="panel-field">
          <label htmlFor="adv-desc">Опис</label>
          <textarea
            id="adv-desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            required
            maxLength={2000}
            rows={5}
          />
        </div>
        <div className="panel-field">
          <label htmlFor="adv-slots">Кількість місць</label>
          <input
            id="adv-slots"
            type="number"
            min={1}
            max={50}
            value={slots}
            onChange={(e) => setSlots(Number(e.target.value))}
            required
          />
        </div>
        <div className="panel-field">
          <label htmlFor="adv-deadline">Дедлайн</label>
          <input
            id="adv-deadline"
            type="datetime-local"
            value={deadline}
            onChange={(e) => setDeadline(e.target.value)}
            required
          />
        </div>
        <div className="panel-field">
          <label htmlFor="adv-contact">Контакт</label>
          <input
            id="adv-contact"
            value={contact}
            onChange={(e) => setContact(e.target.value)}
            required
            maxLength={200}
            placeholder="@telegram або email / телефон"
          />
          <p className="panel-hint">
            Telegram (@нік або t.me/нік), телефон або email. Бачать лише ви й
            підтверджені відгукувачі.
          </p>
        </div>
        {error ? <p className="panel-error">{error}</p> : null}
        <div className="panel-actions">
          <button type="submit" className="orgs-btn" disabled={submitting}>
            {submitting ? 'Створення…' : 'Опублікувати'}
          </button>
          <Link to="/board" className="orgs-btn">
            Скасувати
          </Link>
        </div>
      </form>
    </section>
  )
}

export default AdvertCreatePage

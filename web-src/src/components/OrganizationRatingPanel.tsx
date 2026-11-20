import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getOrganizationRating, rateOrganization } from '../api/ratings'
import { useAuth } from '../auth/AuthContext'
import type { RatingSummary } from '../types/rating'
import { RATING_SCORES } from '../types/rating'
import { apiErrorMessage } from '../utils/apiErrorMessage'
import { formatRating } from '../utils/rating'
import './OrganizationRatingPanel.css'

interface Props {
  organizationId: string
  /** рейтинг, що прийшов разом з організацією (FR-064) */
  initial: RatingSummary | undefined
  loginHref: string
}

/** Рейтинг організації для всіх і оцінювання для залогінених (FR-063, FR-064). */
function OrganizationRatingPanel({
  organizationId,
  initial,
  loginHref,
}: Props) {
  const { isAuthenticated } = useAuth()
  const [summary, setSummary] = useState<RatingSummary | undefined>(initial)
  const [myScore, setMyScore] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setSummary(initial)
  }, [initial])

  useEffect(() => {
    if (!isAuthenticated) {
      setMyScore(null)
      return
    }
    let cancelled = false
    async function load() {
      try {
        const view = await getOrganizationRating(organizationId)
        if (!cancelled) {
          setMyScore(view.myScore)
          setSummary(view.rating)
        }
      } catch {
        // своя оцінка — не головне на сторінці; середнє вже є з організації
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [organizationId, isAuthenticated])

  const rate = async (score: number) => {
    setBusy(true)
    setError(null)
    try {
      const view = await rateOrganization(organizationId, score)
      setMyScore(view.myScore)
      setSummary(view.rating)
    } catch (err) {
      setError(apiErrorMessage(err, 'Не вдалося зберегти оцінку'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="org-rating" aria-label="Рейтинг організації">
      <p className="org-rating__summary">{formatRating(summary)}</p>
      {isAuthenticated ? (
        <div className="org-rating__mine">
          <span className="org-rating__label">
            {myScore === null ? 'Ваша оцінка:' : 'Ваша оцінка (можна змінити):'}
          </span>
          <div
            className="org-rating__stars"
            role="group"
            aria-label="Ваша оцінка"
          >
            {RATING_SCORES.map((score) => (
              <button
                key={score}
                type="button"
                className={
                  myScore !== null && score <= myScore
                    ? 'org-rating__star org-rating__star--on'
                    : 'org-rating__star'
                }
                aria-label={`Оцінити на ${score} з 5`}
                aria-pressed={myScore === score}
                disabled={busy}
                onClick={() => void rate(score)}
              >
                ★
              </button>
            ))}
          </div>
        </div>
      ) : (
        <Link to={loginHref} className="org-rating__login">
          Увійдіть, щоб оцінити
        </Link>
      )}
      {error ? (
        <p className="orgs-page__status orgs-page__status--error" role="alert">
          {error}
        </p>
      ) : null}
    </section>
  )
}

export default OrganizationRatingPanel

import type { RatingSummary } from '../types/rating'

/** «1 оцінка», «3 оцінки», «5 оцінок», «11 оцінок», «21 оцінка». */
export function ratingCountLabel(count: number): string {
  const lastTwo = count % 100
  const last = count % 10
  if (lastTwo >= 11 && lastTwo <= 14) return `${count} оцінок`
  if (last === 1) return `${count} оцінка`
  if (last >= 2 && last <= 4) return `${count} оцінки`
  return `${count} оцінок`
}

/** «★ 4.5 · 12 оцінок» або «Ще немає оцінок». */
export function formatRating(summary: RatingSummary | undefined): string {
  if (!summary || summary.count === 0 || summary.average === null) {
    return 'Ще немає оцінок'
  }
  return `★ ${summary.average.toFixed(1)} · ${ratingCountLabel(summary.count)}`
}

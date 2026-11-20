/** Рейтинг: середня оцінка 1–5 (до десятих) і кількість оцінок (FR-062, FR-064). */
export interface RatingSummary {
  /** null, поки оцінок немає */
  average: number | null
  count: number
}

/** Рейтинг організації з погляду поточного користувача (FR-063). */
export interface OrganizationRating {
  organizationId: string
  rating: RatingSummary
  /** ваша оцінка або null, якщо ще не оцінювали */
  myScore: number | null
}

/** Відповідь на оцінку автора оголошення (FR-061). */
export interface AuthorRatingResponse {
  advertId: string
  authorId: string
  score: number
  authorRating: RatingSummary
  createdAt: string
}

export const RATING_SCORES = [1, 2, 3, 4, 5] as const

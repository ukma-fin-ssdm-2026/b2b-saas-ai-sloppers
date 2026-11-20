import type { AuthorRatingResponse, OrganizationRating } from '../types/rating'
import { apiFetch } from './client'

export { ApiError } from './client'

/** FR-063, FR-064: рейтинг організації і ваша оцінка (потрібен вхід). */
export async function getOrganizationRating(
  organizationId: string,
): Promise<OrganizationRating> {
  return apiFetch<OrganizationRating>(`/organizations/${organizationId}/rating`)
}

/** FR-063: поставити або змінити оцінку організації. */
export async function rateOrganization(
  organizationId: string,
  score: number,
): Promise<OrganizationRating> {
  return apiFetch<OrganizationRating>(
    `/organizations/${organizationId}/rating`,
    { method: 'PUT', body: JSON.stringify({ score }) },
  )
}

/** FR-061: оцінити автора закритого оголошення, один раз. */
export async function rateAdvertAuthor(
  advertId: string,
  score: number,
): Promise<AuthorRatingResponse> {
  return apiFetch<AuthorRatingResponse>(`/adverts/${advertId}/rating`, {
    method: 'POST',
    body: JSON.stringify({ score }),
  })
}

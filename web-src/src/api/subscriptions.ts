import { apiFetch } from './client'

export { ApiError } from './client'

export type SubscriptionStatus = {
  subscribed: boolean
}

export async function getOrganizationSubscription(
  organizationId: string,
): Promise<SubscriptionStatus> {
  return apiFetch<SubscriptionStatus>(
    `/organizations/${organizationId}/subscription`,
  )
}

export async function subscribeToOrganization(
  organizationId: string,
): Promise<void> {
  await apiFetch<void>(`/organizations/${organizationId}/subscription`, {
    method: 'POST',
  })
}

export async function unsubscribeFromOrganization(
  organizationId: string,
): Promise<void> {
  await apiFetch<void>(`/organizations/${organizationId}/subscription`, {
    method: 'DELETE',
  })
}

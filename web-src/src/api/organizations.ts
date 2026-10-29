import type {
  ListOrganizationsParams,
  OffsetPageOrganization,
  Organization,
} from '../types/organization'
import { apiFetch } from './client'

export { ApiError } from './client'

export async function listOrganizations(
  params: ListOrganizationsParams = {},
): Promise<OffsetPageOrganization> {
  const query = new URLSearchParams()

  if (params.offset !== undefined) {
    query.set('offset', String(params.offset))
  }
  if (params.limit !== undefined) {
    query.set('limit', String(params.limit))
  }
  if (params.q !== undefined && params.q !== '') {
    query.set('q', params.q)
  }

  const qs = query.toString()
  return apiFetch<OffsetPageOrganization>(
    `/organizations${qs ? `?${qs}` : ''}`,
  )
}

export async function getOrganization(
  organizationId: string,
): Promise<Organization> {
  return apiFetch<Organization>(`/organizations/${organizationId}`)
}

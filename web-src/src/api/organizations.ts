import type {
  ListOrganizationsParams,
  OffsetPageOrganization,
  Organization,
  OrganizationCreateRequest,
  OrganizationUpdateRequest,
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

export async function createOrganization(
  body: OrganizationCreateRequest,
): Promise<Organization> {
  return apiFetch<Organization>('/organizations', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function updateOrganization(
  organizationId: string,
  body: OrganizationUpdateRequest,
): Promise<Organization> {
  return apiFetch<Organization>(`/organizations/${organizationId}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}

export async function deleteOrganization(
  organizationId: string,
): Promise<void> {
  await apiFetch<void>(`/organizations/${organizationId}`, {
    method: 'DELETE',
  })
}

export async function uploadOrganizationLogo(
  organizationId: string,
  file: File,
): Promise<Organization> {
  const form = new FormData()
  form.append('file', file)
  return apiFetch<Organization>(`/organizations/${organizationId}/logo`, {
    method: 'PUT',
    body: form,
  })
}

export async function deleteOrganizationLogo(
  organizationId: string,
): Promise<void> {
  await apiFetch<void>(`/organizations/${organizationId}/logo`, {
    method: 'DELETE',
  })
}

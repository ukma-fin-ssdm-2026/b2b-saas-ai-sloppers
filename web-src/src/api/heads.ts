import type {
  AppointHeadRequest,
  OrganizationHead,
  RoleChange,
} from '../types/head'
import { apiFetch } from './client'

export { ApiError } from './client'

export async function listHeads(
  organizationId: string,
): Promise<OrganizationHead[]> {
  return apiFetch<OrganizationHead[]>(`/organizations/${organizationId}/heads`)
}

export async function appointHead(
  organizationId: string,
  body: AppointHeadRequest,
): Promise<OrganizationHead> {
  return apiFetch<OrganizationHead>(`/organizations/${organizationId}/heads`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function removeHead(
  organizationId: string,
  userId: string,
): Promise<void> {
  await apiFetch<void>(`/organizations/${organizationId}/heads/${userId}`, {
    method: 'DELETE',
  })
}

export async function roleLog(organizationId: string): Promise<RoleChange[]> {
  return apiFetch<RoleChange[]>(`/organizations/${organizationId}/role-log`)
}

import type {
  Ban,
  BanRequest,
  BannedWord,
  Complaint,
  ComplaintCreateRequest,
  ListComplaintsParams,
  OffsetPageComplaint,
} from '../types/moderation'
import { apiFetch } from './client'

export { ApiError } from './client'

/** FR-056: скарга від будь-якого залогіненого користувача. */
export async function createComplaint(
  body: ComplaintCreateRequest,
): Promise<Complaint> {
  return apiFetch<Complaint>('/complaints', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

/** FR-057: черга скарг, лише Global Admin. */
export async function listComplaints(
  params: ListComplaintsParams = {},
): Promise<OffsetPageComplaint> {
  const query = new URLSearchParams()
  if (params.status !== undefined) query.set('status', params.status)
  if (params.limit !== undefined) query.set('limit', String(params.limit))
  if (params.offset !== undefined) query.set('offset', String(params.offset))
  const qs = query.toString()
  return apiFetch<OffsetPageComplaint>(
    `/moderation/complaints${qs ? `?${qs}` : ''}`,
  )
}

function decisionBody(resolution?: string): string {
  const trimmed = resolution?.trim()
  return JSON.stringify(trimmed ? { resolution: trimmed } : {})
}

/** Підтвердити скаргу: контент прибирається. */
export async function resolveComplaint(
  complaintId: string,
  resolution?: string,
): Promise<Complaint> {
  return apiFetch<Complaint>(`/moderation/complaints/${complaintId}/resolve`, {
    method: 'POST',
    body: decisionBody(resolution),
  })
}

/** Відхилити скаргу: контент лишається. */
export async function dismissComplaint(
  complaintId: string,
  resolution?: string,
): Promise<Complaint> {
  return apiFetch<Complaint>(`/moderation/complaints/${complaintId}/dismiss`, {
    method: 'POST',
    body: decisionBody(resolution),
  })
}

/** FR-054: заблокувати на строк (expiresAt) або безстроково. */
export async function banUser(userId: string, body: BanRequest): Promise<Ban> {
  return apiFetch<Ban>(`/moderation/users/${userId}/ban`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function unbanUser(userId: string): Promise<Ban> {
  return apiFetch<Ban>(`/moderation/users/${userId}/ban`, { method: 'DELETE' })
}

export async function listBans(userId: string): Promise<Ban[]> {
  return apiFetch<Ban[]>(`/moderation/users/${userId}/bans`)
}

/** FR-058: список автофільтра. */
export async function listBannedWords(): Promise<BannedWord[]> {
  return apiFetch<BannedWord[]>('/moderation/banned-words')
}

export async function addBannedWord(word: string): Promise<BannedWord> {
  return apiFetch<BannedWord>('/moderation/banned-words', {
    method: 'POST',
    body: JSON.stringify({ word: word.trim() }),
  })
}

export async function removeBannedWord(wordId: string): Promise<void> {
  return apiFetch<void>(`/moderation/banned-words/${wordId}`, {
    method: 'DELETE',
  })
}

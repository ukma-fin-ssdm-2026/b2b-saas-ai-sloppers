import type {
  Advert,
  AdvertCreateRequest,
  AdvertReply,
  AdvertType,
  ListAdvertsParams,
  OffsetPageAdvert,
} from '../types/advert'
import { apiFetch } from './client'

export { ApiError } from './client'

function pageQuery(params: { offset?: number; limit?: number } = {}): string {
  const query = new URLSearchParams()
  if (params.offset !== undefined) query.set('offset', String(params.offset))
  if (params.limit !== undefined) query.set('limit', String(params.limit))
  const qs = query.toString()
  return qs ? `?${qs}` : ''
}

export async function listAdverts(
  params: ListAdvertsParams = {},
): Promise<OffsetPageAdvert> {
  const query = new URLSearchParams()
  if (params.type) query.set('type', params.type)
  if (params.offset !== undefined) query.set('offset', String(params.offset))
  if (params.limit !== undefined) query.set('limit', String(params.limit))
  const qs = query.toString()
  return apiFetch<OffsetPageAdvert>(`/adverts${qs ? `?${qs}` : ''}`)
}

export async function listMyAdverts(params: {
  offset?: number
  limit?: number
} = {}): Promise<OffsetPageAdvert> {
  return apiFetch<OffsetPageAdvert>(`/adverts/mine${pageQuery(params)}`)
}

export async function listRepliedAdverts(params: {
  offset?: number
  limit?: number
} = {}): Promise<OffsetPageAdvert> {
  return apiFetch<OffsetPageAdvert>(`/adverts/replied${pageQuery(params)}`)
}

export async function getAdvert(advertId: string): Promise<Advert> {
  return apiFetch<Advert>(`/adverts/${advertId}`)
}

export async function createAdvert(
  body: AdvertCreateRequest,
): Promise<Advert> {
  return apiFetch<Advert>('/adverts', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function closeAdvert(advertId: string): Promise<Advert> {
  return apiFetch<Advert>(`/adverts/${advertId}/close`, { method: 'POST' })
}

export async function deleteAdvert(advertId: string): Promise<void> {
  await apiFetch<void>(`/adverts/${advertId}`, { method: 'DELETE' })
}

export async function listAdvertReplies(
  advertId: string,
): Promise<AdvertReply[]> {
  return apiFetch<AdvertReply[]>(`/adverts/${advertId}/replies`)
}

export async function replyToAdvert(
  advertId: string,
  message?: string,
): Promise<AdvertReply> {
  return apiFetch<AdvertReply>(`/adverts/${advertId}/replies`, {
    method: 'POST',
    body: JSON.stringify({ message: message?.trim() || null }),
  })
}

export async function confirmAdvertReply(
  advertId: string,
  replyId: string,
): Promise<AdvertReply> {
  return apiFetch<AdvertReply>(
    `/adverts/${advertId}/replies/${replyId}/confirm`,
    { method: 'POST' },
  )
}

export async function rejectAdvertReply(
  advertId: string,
  replyId: string,
): Promise<AdvertReply> {
  return apiFetch<AdvertReply>(
    `/adverts/${advertId}/replies/${replyId}/reject`,
    { method: 'POST' },
  )
}

export async function withdrawAdvertReply(
  advertId: string,
  replyId: string,
): Promise<AdvertReply> {
  return apiFetch<AdvertReply>(
    `/adverts/${advertId}/replies/${replyId}/withdraw`,
    { method: 'POST' },
  )
}

export type { AdvertType }

import type {
  Advert,
  AdvertCreateRequest,
  ListAdvertsParams,
  OffsetPageAdvert,
} from '../types/advert'
import { apiFetch } from './client'

export { ApiError } from './client'

function pageQuery(params: ListAdvertsParams): string {
  const query = new URLSearchParams()

  if (params.type !== undefined) {
    query.set('type', params.type)
  }
  if (params.limit !== undefined) {
    query.set('limit', String(params.limit))
  }
  if (params.offset !== undefined) {
    query.set('offset', String(params.offset))
  }

  const qs = query.toString()
  return qs ? `?${qs}` : ''
}

export async function listAdverts(
  params: ListAdvertsParams = {},
): Promise<OffsetPageAdvert> {
  return apiFetch<OffsetPageAdvert>(`/adverts${pageQuery(params)}`)
}

export async function listMyAdverts(
  params: Omit<ListAdvertsParams, 'type'> = {},
): Promise<OffsetPageAdvert> {
  return apiFetch<OffsetPageAdvert>(`/adverts/mine${pageQuery(params)}`)
}

export async function listRepliedAdverts(
  params: Omit<ListAdvertsParams, 'type'> = {},
): Promise<OffsetPageAdvert> {
  return apiFetch<OffsetPageAdvert>(`/adverts/replied${pageQuery(params)}`)
}

export async function getAdvert(advertId: string): Promise<Advert> {
  return apiFetch<Advert>(`/adverts/${advertId}`)
}

export async function createAdvert(body: AdvertCreateRequest): Promise<Advert> {
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

import type { Event, ListEventsParams, OffsetPageEvent } from '../types/event'
import { apiFetch } from './client'

export { ApiError } from './client'

export async function listEvents(
  params: ListEventsParams = {},
): Promise<OffsetPageEvent> {
  const query = new URLSearchParams()

  if (params.organizationId !== undefined) {
    query.set('organizationId', params.organizationId)
  }
  if (params.from !== undefined) {
    query.set('from', params.from)
  }
  if (params.to !== undefined) {
    query.set('to', params.to)
  }
  if (params.offset !== undefined) {
    query.set('offset', String(params.offset))
  }
  if (params.limit !== undefined) {
    query.set('limit', String(params.limit))
  }

  const qs = query.toString()
  return apiFetch<OffsetPageEvent>(`/events${qs ? `?${qs}` : ''}`)
}

export async function getEvent(eventId: string): Promise<Event> {
  return apiFetch<Event>(`/events/${eventId}`)
}

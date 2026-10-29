import type {
  Event,
  EventCreateRequest,
  EventUpdateRequest,
  ListEventsParams,
  ListOrganizationEventsParams,
  OffsetPageEvent,
} from '../types/event'
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

export async function listOrganizationEvents(
  organizationId: string,
  params: ListOrganizationEventsParams = {},
): Promise<OffsetPageEvent> {
  const query = new URLSearchParams()

  if (params.status !== undefined) {
    query.set('status', params.status)
  }
  if (params.verified !== undefined) {
    query.set('verified', String(params.verified))
  }
  if (params.offset !== undefined) {
    query.set('offset', String(params.offset))
  }
  if (params.limit !== undefined) {
    query.set('limit', String(params.limit))
  }

  const qs = query.toString()
  return apiFetch<OffsetPageEvent>(
    `/organizations/${organizationId}/events${qs ? `?${qs}` : ''}`,
  )
}

export async function createEvent(
  organizationId: string,
  body: EventCreateRequest,
): Promise<Event> {
  return apiFetch<Event>(`/organizations/${organizationId}/events`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function updateEvent(
  eventId: string,
  body: EventUpdateRequest,
): Promise<Event> {
  return apiFetch<Event>(`/events/${eventId}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}

export async function approveEvent(eventId: string): Promise<Event> {
  return apiFetch<Event>(`/events/${eventId}/approve`, { method: 'POST' })
}

export async function rejectEvent(eventId: string): Promise<Event> {
  return apiFetch<Event>(`/events/${eventId}/reject`, { method: 'POST' })
}

export async function unpublishEvent(eventId: string): Promise<Event> {
  return apiFetch<Event>(`/events/${eventId}/unpublish`, { method: 'POST' })
}

export async function uploadEventImage(
  eventId: string,
  file: File,
): Promise<Event> {
  const form = new FormData()
  form.append('file', file)
  return apiFetch<Event>(`/events/${eventId}/image`, {
    method: 'PUT',
    body: form,
  })
}

export async function deleteEventImage(eventId: string): Promise<void> {
  await apiFetch<void>(`/events/${eventId}/image`, { method: 'DELETE' })
}

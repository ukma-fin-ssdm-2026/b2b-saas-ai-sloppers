import { API_BASE, apiFetch } from './client'

export { ApiError } from './client'

export interface GoogleCalendarLink {
  url: string
}

export async function getGoogleCalendarLink(
  eventId: string,
): Promise<GoogleCalendarLink> {
  return apiFetch<GoogleCalendarLink>(`/events/${eventId}/calendar/google`)
}

export function eventIcsPath(eventId: string): string {
  return `${API_BASE}/events/${eventId}/calendar.ics`
}

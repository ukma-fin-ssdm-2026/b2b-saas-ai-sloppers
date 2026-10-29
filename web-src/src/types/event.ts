import type { OffsetPage } from './pagination'

export type EventStatus =
  | 'PENDING'
  | 'PUBLISHED'
  | 'REJECTED'
  | 'UNPUBLISHED'

export interface Event {
  id: string
  organizationId: string
  title: string
  startsAt: string
  status: EventStatus
  verified: boolean
  scraped: boolean
  createdAt: string
  updatedAt: string
  description?: string
  location?: string
  originalUrl?: string
  imageUrl?: string
}

export type OffsetPageEvent = OffsetPage<Event>

export interface ListEventsParams {
  organizationId?: string
  from?: string
  to?: string
  offset?: number
  limit?: number
}

export interface ListOrganizationEventsParams {
  status?: EventStatus
  verified?: boolean
  offset?: number
  limit?: number
}

export interface EventCreateRequest {
  title: string
  startsAt: string
  description?: string
  location?: string
  originalUrl?: string
}

export interface EventUpdateRequest {
  title?: string
  description?: string
  startsAt?: string
  location?: string
  originalUrl?: string
}

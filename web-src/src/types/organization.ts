import type { OffsetPage } from './pagination'
import type { RatingSummary } from './rating'

export interface Organization {
  id: string
  name: string
  slug: string
  autoPublish: boolean
  createdAt: string
  updatedAt: string
  description?: string
  contactEmail?: string
  instagramHandle?: string
  logoUrl?: string
  /** рейтинг у каталозі й профілі (FR-064); немає у відповідях створення й редагування */
  rating?: RatingSummary
}

export type OffsetPageOrganization = OffsetPage<Organization>

export interface ListOrganizationsParams {
  offset?: number
  limit?: number
  q?: string
}

export interface OrganizationCreateRequest {
  name: string
  slug: string
  /** First head: registered + verified @ukma.edu.ua user (required by API). */
  headEmail: string
  description?: string
  instagramHandle?: string
  contactEmail?: string
}

export interface OrganizationUpdateRequest {
  name?: string
  slug?: string
  description?: string
  instagramHandle?: string
  contactEmail?: string
  autoPublish?: boolean
}

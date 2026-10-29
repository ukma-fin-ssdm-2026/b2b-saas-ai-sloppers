import type { OffsetPage } from './pagination'

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

export interface Organization {
  id: string
  name: string
  slug: string
  createdAt: string
  updatedAt: string
  description?: string
  contactEmail?: string
  instagramHandle?: string
}

export interface OffsetPagination {
  offset: number
  limit: number
  total: number
  hasMore: boolean
}

export interface OffsetPageOrganization {
  data: Organization[]
  pagination: OffsetPagination
}

export interface ListOrganizationsParams {
  offset?: number
  limit?: number
  q?: string
}

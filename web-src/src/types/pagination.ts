export interface OffsetPagination {
  offset: number
  limit: number
  total: number
  hasMore: boolean
}

export interface OffsetPage<T> {
  data: T[]
  pagination: OffsetPagination
}

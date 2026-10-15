import type {
  ListOrganizationsParams,
  OffsetPageOrganization,
} from '../types/organization'

const API_BASE = '/api/v1'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function listOrganizations(
  params: ListOrganizationsParams = {},
): Promise<OffsetPageOrganization> {
  const query = new URLSearchParams()

  if (params.offset !== undefined) {
    query.set('offset', String(params.offset))
  }
  if (params.limit !== undefined) {
    query.set('limit', String(params.limit))
  }
  if (params.q !== undefined && params.q !== '') {
    query.set('q', params.q)
  }

  const qs = query.toString()
  const url = `${API_BASE}/organizations${qs ? `?${qs}` : ''}`

  const response = await fetch(url)

  if (!response.ok) {
    const body = await response.text()
    throw new ApiError(
      response.status,
      body || `Request failed with status ${response.status}`,
    )
  }

  return (await response.json()) as OffsetPageOrganization
}

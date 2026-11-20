import type { OffsetPage } from './pagination'

export type AdvertType = 'TEAM' | 'LEISURE'

export type AdvertStatus = 'ACTIVE' | 'CLOSED' | 'DELETED'

export type ReplyStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'WITHDRAWN'

export interface Advert {
  id: string
  type: AdvertType
  title: string
  description: string
  slots: number
  deadline: string
  status: AdvertStatus
  own: boolean
  createdAt: string
  /** Лише автор і підтверджені відгукувачі (FR-049) */
  contact: string | null
  /** null, якщо автор не задав імені (FR-069) */
  authorName: string | null
  /** null, якщо ви не відгукувалися */
  myReplyStatus: ReplyStatus | null
}

export type OffsetPageAdvert = OffsetPage<Advert>

export interface ListAdvertsParams {
  type?: AdvertType
  limit?: number
  offset?: number
}

export interface AdvertCreateRequest {
  type: AdvertType
  title: string
  description: string
  slots: number
  deadline: string
  contact: string
}

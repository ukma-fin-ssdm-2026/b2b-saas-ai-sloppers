import type { OffsetPage } from './pagination'

export type AdvertType = 'TEAM' | 'LEISURE'
export type AdvertStatus = 'ACTIVE' | 'CLOSED' | 'DELETED'
export type ReplyStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'WITHDRAWN'

export type Advert = {
  id: string
  type: AdvertType
  title: string
  description: string
  slots: number
  deadline: string
  status: AdvertStatus
  contact: string | null
  own: boolean
  authorName: string | null
  myReplyStatus: ReplyStatus | null
  myReplyId: string | null
  createdAt: string
}

export type AdvertCreateRequest = {
  type: AdvertType
  title: string
  description: string
  slots: number
  deadline: string
  contact: string
}

export type AdvertReply = {
  id: string
  advertId: string
  userId: string
  userName: string | null
  message: string | null
  status: ReplyStatus
  createdAt: string
  decidedAt: string | null
}

export type ListAdvertsParams = {
  type?: AdvertType
  offset?: number
  limit?: number
}

export type OffsetPageAdvert = OffsetPage<Advert>

export const ADVERT_TYPE_LABELS: Record<AdvertType, string> = {
  TEAM: 'Команда',
  LEISURE: 'Дозвілля',
}

export const ADVERT_STATUS_LABELS: Record<AdvertStatus, string> = {
  ACTIVE: 'Активне',
  CLOSED: 'Закрите',
  DELETED: 'Видалене',
}

export const REPLY_STATUS_LABELS: Record<ReplyStatus, string> = {
  PENDING: 'Очікує',
  CONFIRMED: 'Підтверджено',
  REJECTED: 'Відхилено',
  WITHDRAWN: 'Відкликано',
}

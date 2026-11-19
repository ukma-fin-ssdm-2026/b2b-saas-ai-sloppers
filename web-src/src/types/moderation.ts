import type { OffsetPage } from './pagination'

export type ContentType = 'ADVERT' | 'REPLY'
export type ComplaintCategory = 'SPAM' | 'ABUSE' | 'OTHER'
export type ComplaintStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED'

/** Скарга на оголошення або відгук (FR-056, FR-057). */
export interface Complaint {
  id: string
  reporterId: string
  /** автор оскарженого контенту */
  authorId: string
  authorName: string | null
  targetType: ContentType
  targetId: string
  category: ComplaintCategory
  comment: string | null
  status: ComplaintStatus
  resolvedBy: string | null
  resolution: string | null
  createdAt: string
  resolvedAt: string | null
}

export type OffsetPageComplaint = OffsetPage<Complaint>

export interface ComplaintCreateRequest {
  targetType: ContentType
  targetId: string
  category: ComplaintCategory
  comment?: string
}

export interface ListComplaintsParams {
  status?: ComplaintStatus
  limit?: number
  offset?: number
}

/** Блокування користувача (FR-054); bannedBy === null — автоблокування (FR-060). */
export interface Ban {
  id: string
  userId: string
  bannedBy: string | null
  automatic: boolean
  reason: string
  /** null — безстроково */
  expiresAt: string | null
  active: boolean
  createdAt: string
  liftedAt: string | null
  liftedBy: string | null
}

export interface BanRequest {
  reason: string
  expiresAt?: string | null
}

/** Слово зі списку автофільтра (FR-058). */
export interface BannedWord {
  id: string
  word: string
  addedBy: string | null
  createdAt: string
}

export const COMPLAINT_CATEGORY_LABELS: Record<ComplaintCategory, string> = {
  SPAM: 'Спам',
  ABUSE: 'Образи',
  OTHER: 'Інше',
}

export const COMPLAINT_STATUS_LABELS: Record<ComplaintStatus, string> = {
  OPEN: 'Відкрита',
  RESOLVED: 'Контент прибрано',
  DISMISSED: 'Відхилена',
}

export const CONTENT_TYPE_LABELS: Record<ContentType, string> = {
  ADVERT: 'Оголошення',
  REPLY: 'Відгук',
}

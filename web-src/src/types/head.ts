export interface OrganizationHead {
  userId: string
  email: string
  appointedAt: string
  /** null, якщо користувача, який призначив, уже видалено */
  appointedBy?: string | null
}

export type RoleAction = 'APPOINTED' | 'REMOVED'

export interface RoleChange {
  id: string
  organizationId: string
  targetUserId: string
  targetEmail: string
  action: RoleAction
  createdAt: string
  actorUserId?: string | null
  actorEmail?: string | null
}

export interface AppointHeadRequest {
  email: string
  /** Знімає цього голову в тій самій операції (FR-012, лише Global Admin) */
  replacesUserId?: string
}

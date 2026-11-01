import { beforeEach, describe, expect, it } from 'vitest'
import { mockStore } from './store'

describe('mockStore', () => {
  beforeEach(() => {
    mockStore.reset()
  })

  describe('organizations', () => {
    it('seed містить організації, відсортовані за назвою в list', () => {
      const page = mockStore.listOrganizations(undefined, 20, 0)
      expect(page.pagination.total).toBeGreaterThanOrEqual(2)
      const names = page.data.map((o) => o.name)
      expect([...names].sort((a, b) => a.localeCompare(b, 'uk'))).toEqual(names)
    })

    it('пошук q фільтрує за назвою/slug', () => {
      mockStore.createOrganization({
        name: 'Унікальний клуб XYZ',
        slug: 'unique-xyz',
      })
      const page = mockStore.listOrganizations('unique-xyz', 20, 0)
      expect(page.data).toHaveLength(1)
      expect(page.data[0]?.slug).toBe('unique-xyz')
    })

    it('дубль slug → SLUG_CONFLICT', () => {
      mockStore.createOrganization({ name: 'One', slug: 'same-slug' })
      expect(() =>
        mockStore.createOrganization({ name: 'Two', slug: 'same-slug' }),
      ).toThrow('SLUG_CONFLICT')
    })

    it('deleteOrganization прибирає org (події каскадом)', () => {
      const org = mockStore.createOrganization({
        name: 'To Delete',
        slug: 'to-delete',
      })
      mockStore.createEvent(org.id, {
        title: 'Event to cascade',
        startsAt: new Date(Date.now() + 86400000).toISOString(),
      })
      const totalBefore = mockStore.listOrganizations(undefined, 100, 0)
        .pagination.total
      mockStore.deleteOrganization(org.id)
      expect(() => mockStore.getOrganization(org.id)).toThrow(
        'ORGANIZATION_NOT_FOUND',
      )
      expect(
        mockStore.listOrganizations(undefined, 100, 0).pagination.total,
      ).toBe(totalBefore - 1)
    })
  })

  describe('events / moderation', () => {
    it('createEvent одразу PUBLISHED і verified', () => {
      const org = mockStore.listOrganizations(undefined, 1, 0).data[0]!
      const created = mockStore.createEvent(org.id, {
        title: 'Live talk',
        startsAt: new Date(Date.now() + 86400000).toISOString(),
        location: '1 корпус',
      })
      expect(created.status).toBe('PUBLISHED')
      expect(created.verified).toBe(true)
      expect(created.scraped).toBe(false)
    })

    it('addPendingScraped → approve робить PUBLISHED+verified', () => {
      const org = mockStore.listOrganizations(undefined, 1, 0).data[0]!
      const pending = mockStore.addPendingScraped(org.id, 'Scraped title')
      expect(pending.status).toBe('PENDING')
      expect(pending.scraped).toBe(true)

      const approved = mockStore.approveEvent(pending.id)
      expect(approved.status).toBe('PUBLISHED')
      expect(approved.verified).toBe(true)
    })

    it('reject / unpublish змінюють статус', () => {
      const org = mockStore.listOrganizations(undefined, 1, 0).data[0]!
      const pending = mockStore.addPendingScraped(org.id, 'To reject')
      expect(mockStore.rejectEvent(pending.id).status).toBe('REJECTED')

      const published = mockStore.createEvent(org.id, {
        title: 'To unpublish',
        startsAt: new Date(Date.now() + 86400000).toISOString(),
      })
      expect(mockStore.unpublishEvent(published.id).status).toBe('UNPUBLISHED')
    })

    it('listOrgEvents фільтрує status і verified', () => {
      const org = mockStore.listOrganizations(undefined, 1, 0).data[0]!
      mockStore.addPendingScraped(org.id, 'P1')
      const pendingOnly = mockStore.listOrgEvents(org.id, { status: 'PENDING' })
      expect(pendingOnly.data.every((e) => e.status === 'PENDING')).toBe(true)

      const unverified = mockStore.listOrgEvents(org.id, { verified: false })
      expect(unverified.data.every((e) => e.verified === false)).toBe(true)
    })

    it('updateEvent змінює title', () => {
      const org = mockStore.listOrganizations(undefined, 1, 0).data[0]!
      const created = mockStore.createEvent(org.id, {
        title: 'Old',
        startsAt: new Date(Date.now() + 86400000).toISOString(),
      })
      const updated = mockStore.updateEvent(created.id, { title: 'New' })
      expect(updated.title).toBe('New')
    })
  })

  describe('reset', () => {
    it('скидає до seed після мутацій', () => {
      const before = mockStore.listOrganizations(undefined, 100, 0).pagination.total
      mockStore.createOrganization({ name: 'Extra', slug: 'extra-org' })
      expect(
        mockStore.listOrganizations(undefined, 100, 0).pagination.total,
      ).toBe(before + 1)
      mockStore.reset()
      expect(
        mockStore.listOrganizations(undefined, 100, 0).pagination.total,
      ).toBe(before)
    })
  })
})

import type { Event } from '../../types/event'
import type { Organization } from '../../types/organization'

/** Fixed reference time — mirrors backend TestFixtures.NOW */
export const NOW = new Date('2026-10-21T10:00:00Z')

export const ORG_ID = '3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60'
export const OTHER_ORG_ID = 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d'
export const EVENT_ID = 'e1000000-0000-4000-8000-000000000001'

export function organization(
  id = ORG_ID,
  name = 'Дебатний клуб НаУКМА',
  slug = 'debate-club',
  autoPublish = false,
): Organization {
  const createdAt = new Date(NOW.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString()
  return {
    id,
    name,
    slug,
    autoPublish,
    description: `Опис ${name}`,
    contactEmail: `${slug}@ukma.edu.ua`,
    instagramHandle: slug.replaceAll('-', '.'),
    createdAt,
    updatedAt: createdAt,
  }
}

export function event(overrides: Partial<Event> = {}): Event {
  const createdAt = new Date(NOW.getTime() - 24 * 60 * 60 * 1000).toISOString()
  const startsAt = new Date(NOW.getTime() + 24 * 60 * 60 * 1000).toISOString()
  return {
    id: EVENT_ID,
    organizationId: ORG_ID,
    title: 'Тестова подія',
    startsAt,
    status: 'PUBLISHED',
    verified: true,
    scraped: false,
    createdAt,
    updatedAt: createdAt,
    description: 'Опис',
    location: 'НаУКМА',
    ...overrides,
  }
}

export function publishedEvent(startsAt: Date): Event {
  return event({
    id: crypto.randomUUID(),
    startsAt: startsAt.toISOString(),
    status: 'PUBLISHED',
    verified: true,
  })
}

import type { Event, EventStatus } from '../types/event'
import type { Organization } from '../types/organization'
import type { OffsetPage } from '../types/pagination'

function nowIso(): string {
  return new Date().toISOString()
}

function id(): string {
  return crypto.randomUUID()
}

function pageOf<T>(items: T[], limit = 20, offset = 0): OffsetPage<T> {
  const slice = items.slice(offset, offset + limit)
  return {
    data: slice,
    pagination: {
      offset,
      limit,
      total: items.length,
      hasMore: offset + limit < items.length,
    },
  }
}

function seed(): { organizations: Organization[]; events: Event[] } {
  const orgA: Organization = {
    id: id(),
    name: 'Дебатний клуб (mock)',
    slug: 'debate-mock',
    autoPublish: false,
    description: 'Mock-організація для тестів фронт',
    contactEmail: 'debate@mock.ukma',
    instagramHandle: 'ukma.debate.mock',
    createdAt: nowIso(),
    updatedAt: nowIso(),
  }
  const orgB: Organization = {
    id: id(),
    name: 'FIDo (mock)',
    slug: 'fido-mock',
    autoPublish: true,
    description: 'Друга mock-організація.',
    createdAt: nowIso(),
    updatedAt: nowIso(),
  }

  const inTwoDays = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString()
  const inFiveDays = new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString()

  const events: Event[] = [
    {
      id: id(),
      organizationId: orgA.id,
      title: 'Тренування з дебатів (PENDING)',
      description: 'Черга модерації — спробуй Approve / Reject.',
      startsAt: inTwoDays,
      location: 'НаУКМА, 1 корпус',
      status: 'PENDING',
      verified: false,
      scraped: true,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    },
    {
      id: id(),
      organizationId: orgA.id,
      title: 'Турнір (PUBLISHED)',
      startsAt: inFiveDays,
      location: 'Актова зала',
      status: 'PUBLISHED',
      verified: true,
      scraped: false,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    },
    {
      id: id(),
      organizationId: orgB.id,
      title: 'Хакатон FIDo (неперевірений)',
      startsAt: inFiveDays,
      status: 'PUBLISHED',
      verified: false,
      scraped: true,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    },
  ]

  return { organizations: [orgA, orgB], events }
}

type Listener = () => void

class MockStore {
  private organizations: Organization[] = []
  private events: Event[] = []
  private listeners = new Set<Listener>()
  private version = 0

  constructor() {
    this.reset()
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  /** Stable snapshot for useSyncExternalStore */
  getVersion(): number {
    return this.version
  }

  private notify(): void {
    this.version += 1
    for (const listener of this.listeners) listener()
  }

  reset(): void {
    const seeded = seed()
    this.organizations = seeded.organizations
    this.events = seeded.events
    this.notify()
  }

  listOrganizations(q?: string, limit = 20, offset = 0) {
    let items = [...this.organizations].sort((a, b) =>
      a.name.localeCompare(b.name, 'uk'),
    )
    if (q) {
      const needle = q.toLowerCase()
      items = items.filter(
        (o) =>
          o.name.toLowerCase().includes(needle) ||
          o.slug.toLowerCase().includes(needle),
      )
    }
    return pageOf(items, limit, offset)
  }

  getOrganization(organizationId: string): Organization {
    const org = this.organizations.find((o) => o.id === organizationId)
    if (!org) throw new Error('ORGANIZATION_NOT_FOUND')
    return org
  }

  createOrganization(input: {
    name: string
    slug: string
    description?: string
    instagramHandle?: string
    contactEmail?: string
  }): Organization {
    if (this.organizations.some((o) => o.slug === input.slug)) {
      throw new Error('SLUG_CONFLICT')
    }
    const org: Organization = {
      id: id(),
      name: input.name,
      slug: input.slug,
      autoPublish: false,
      description: input.description,
      instagramHandle: input.instagramHandle,
      contactEmail: input.contactEmail,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    }
    this.organizations.push(org)
    this.notify()
    return org
  }

  updateOrganization(
    organizationId: string,
    patch: Partial<
      Pick<
        Organization,
        | 'name'
        | 'slug'
        | 'description'
        | 'instagramHandle'
        | 'contactEmail'
        | 'autoPublish'
        | 'logoUrl'
      >
    >,
  ): Organization {
    const idx = this.organizations.findIndex((o) => o.id === organizationId)
    if (idx < 0) throw new Error('ORGANIZATION_NOT_FOUND')
    if (
      patch.slug &&
      this.organizations.some(
        (o) => o.slug === patch.slug && o.id !== organizationId,
      )
    ) {
      throw new Error('SLUG_CONFLICT')
    }
    const updated: Organization = {
      ...this.organizations[idx],
      ...patch,
      updatedAt: nowIso(),
    }
    this.organizations[idx] = updated
    this.notify()
    return updated
  }

  deleteOrganization(organizationId: string): void {
    this.getOrganization(organizationId)
    this.organizations = this.organizations.filter((o) => o.id !== organizationId)
    this.events = this.events.filter((e) => e.organizationId !== organizationId)
    this.notify()
  }

  listOrgEvents(
    organizationId: string,
    opts: { status?: EventStatus; verified?: boolean; limit?: number; offset?: number } = {},
  ) {
    this.getOrganization(organizationId)
    let items = this.events.filter((e) => e.organizationId === organizationId)
    if (opts.status) items = items.filter((e) => e.status === opts.status)
    if (opts.verified !== undefined) {
      items = items.filter((e) => e.verified === opts.verified)
    }
    items = [...items].sort((a, b) => a.startsAt.localeCompare(b.startsAt))
    return pageOf(items, opts.limit ?? 20, opts.offset ?? 0)
  }

  createEvent(
    organizationId: string,
    input: {
      title: string
      startsAt: string
      description?: string
      location?: string
      originalUrl?: string
    },
  ): Event {
    this.getOrganization(organizationId)
    const event: Event = {
      id: id(),
      organizationId,
      title: input.title,
      startsAt: input.startsAt,
      description: input.description,
      location: input.location,
      originalUrl: input.originalUrl,
      status: 'PUBLISHED',
      verified: true,
      scraped: false,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    }
    this.events.push(event)
    this.notify()
    return event
  }

  updateEvent(
    eventId: string,
    patch: Partial<
      Pick<Event, 'title' | 'description' | 'startsAt' | 'location' | 'originalUrl'>
    >,
  ): Event {
    const idx = this.events.findIndex((e) => e.id === eventId)
    if (idx < 0) throw new Error('EVENT_NOT_FOUND')
    const updated: Event = {
      ...this.events[idx],
      ...patch,
      updatedAt: nowIso(),
    }
    this.events[idx] = updated
    this.notify()
    return updated
  }

  private setStatus(eventId: string, status: EventStatus, verified?: boolean): Event {
    const idx = this.events.findIndex((e) => e.id === eventId)
    if (idx < 0) throw new Error('EVENT_NOT_FOUND')
    const current = this.events[idx]
    const updated: Event = {
      ...current,
      status,
      verified: verified ?? current.verified,
      updatedAt: nowIso(),
    }
    this.events[idx] = updated
    this.notify()
    return updated
  }

  approveEvent(eventId: string): Event {
    return this.setStatus(eventId, 'PUBLISHED', true)
  }

  rejectEvent(eventId: string): Event {
    return this.setStatus(eventId, 'REJECTED', true)
  }

  unpublishEvent(eventId: string): Event {
    return this.setStatus(eventId, 'UNPUBLISHED')
  }

  addPendingScraped(organizationId: string, title: string): Event {
    this.getOrganization(organizationId)
    const startsAt = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString()
    const event: Event = {
      id: id(),
      organizationId,
      title,
      startsAt,
      location: 'Mock location',
      status: 'PENDING',
      verified: false,
      scraped: true,
      originalUrl: 'https://www.instagram.com/p/mock/',
      createdAt: nowIso(),
      updatedAt: nowIso(),
    }
    this.events.push(event)
    this.notify()
    return event
  }
}

export const mockStore = new MockStore()

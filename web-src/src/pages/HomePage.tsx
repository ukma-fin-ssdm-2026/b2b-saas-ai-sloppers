import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { listEvents } from '../api/events'
import AccordionGallery, {
  type AccordionGalleryItem,
} from '../components/AccordionGallery'
import type { Event } from '../types/event'
import './HomePage.css'
import './OrganizationsPage.css'

const SLOT_COUNT = 6
/** Expanded share — lower = wider closed columns. */
const EXPAND_RATIO = 0.36
/** Instagram portrait post: 4:5 */
const IG_ASPECT = 4 / 5
const PLACEHOLDER_TONES = [
  '#141414',
  '#1a1a1a',
  '#121212',
  '#181818',
  '#101010',
  '#161616',
]

function placeholderPoster(tone: string, index: number): string {
  // 1080×1350 — Instagram 4:5
  const svg = `
<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1350" viewBox="0 0 1080 1350">
  <defs>
    <linearGradient id="g${index}" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="${tone}"/>
      <stop offset="100%" stop-color="#0a0a0a"/>
    </linearGradient>
  </defs>
  <rect width="1080" height="1350" fill="url(#g${index})"/>
  <rect x="72" y="72" width="936" height="1206" fill="none" stroke="#2a2a2a" stroke-width="2"/>
  <text x="540" y="690" text-anchor="middle" fill="#5a5a5a"
    font-family="Georgia, Times New Roman, serif" font-size="48" letter-spacing="8">
    ОЧІКУЄТЬСЯ
  </text>
</svg>`.trim()

  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

function placeholderItem(index: number): AccordionGalleryItem {
  return {
    image: placeholderPoster(
      PLACEHOLDER_TONES[index % PLACEHOLDER_TONES.length]!,
      index,
    ),
    label: 'Очікується',
    alt: `Слот ${index + 1}: очікується`,
  }
}

function eventToItem(event: Event, index: number): AccordionGalleryItem {
  return {
    image:
      event.imageUrl ||
      placeholderPoster(PLACEHOLDER_TONES[index % PLACEHOLDER_TONES.length]!, index),
    label: event.title,
    alt: event.title,
    link: `/events/${event.id}`,
  }
}

/** Newest created events first (left), then older to the right. */
function newestFirst(a: Event, b: Event): number {
  const byCreated = Date.parse(b.createdAt) - Date.parse(a.createdAt)
  if (byCreated !== 0) return byCreated
  return Date.parse(b.startsAt) - Date.parse(a.startsAt)
}

function buildGallerySlots(events: Event[]): AccordionGalleryItem[] {
  const queue = [...events].sort(newestFirst).slice(0, SLOT_COUNT)
  const slots: AccordionGalleryItem[] = queue.map((event, index) =>
    eventToItem(event, index),
  )
  while (slots.length < SLOT_COUNT) {
    slots.push(placeholderItem(slots.length))
  }
  return slots
}

function HomePage() {
  const galleryRef = useRef<HTMLDivElement>(null)
  const [events, setEvents] = useState<Event[]>([])
  const [galleryHeight, setGalleryHeight] = useState(560)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        // Fetch a page; we re-sort by createdAt client-side for the left→right queue.
        const page = await listEvents({ limit: 50, offset: 0 })
        if (!cancelled) setEvents(page.data)
      } catch {
        if (!cancelled) setEvents([])
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [])

  // Keep the expanded panel close to Instagram 4:5 (no stretchy wide strip).
  useEffect(() => {
    const el = galleryRef.current
    if (!el) return

    const measure = () => {
      const width = el.getBoundingClientRect().width
      if (!width) return
      const expandedWidth = width * EXPAND_RATIO
      const igHeight = expandedWidth / IG_ASPECT
      setGalleryHeight(Math.round(Math.min(720, Math.max(400, igHeight))))
    }

    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const items = useMemo(() => buildGallerySlots(events), [events])
  const filledCount = Math.min(events.length, SLOT_COUNT)

  return (
    <main className="home-page">
      <div className="home-page__inner">
        <div className="home-page__heading">
          <h1 className="orgs-page__title home-page__title">Події</h1>
          <Link to="/events" className="home-page__all-link">
            Усі події →
          </Link>
        </div>

        <div className="home-page__gallery" ref={galleryRef}>
          <AccordionGallery
            key={`gallery-${filledCount}-${events[0]?.id ?? 'empty'}`}
            items={items}
            defaultIndex={0}
            expandRatio={EXPAND_RATIO}
            trigger="hover"
            accentColor="#f2f2f2"
            overlayColor="#0a0a0a"
            textColor="#f2f2f2"
            grayscale
            showLabels
            duration={0.6}
            ease="power3.out"
            parallax={0.5}
            tilt={8}
            stagger={0.06}
            height={galleryHeight}
            gap={12}
            radius={16}
            orientation="horizontal"
          />
        </div>
      </div>
    </main>
  )
}

export default HomePage

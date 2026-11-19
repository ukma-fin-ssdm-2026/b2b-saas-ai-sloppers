import { render, waitFor } from '@testing-library/react'
import { createRef } from 'react'
import { describe, expect, it } from 'vitest'
import CircularCarousel, {
  type CircularCarouselHandle,
} from './CircularCarousel'

const ITEMS = [
  { src: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=', title: 'Alpha' },
  { src: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=', title: 'Bravo' },
  { src: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=', title: 'Charlie' },
]

describe('CircularCarousel focusIndex', () => {
  it('exposes focusIndex via ref and accepts out-of-range indices safely', async () => {
    const ref = createRef<CircularCarouselHandle>()

    render(
      <div style={{ width: 640, height: 360 }}>
        <CircularCarousel
          ref={ref}
          items={ITEMS}
          intro="none"
          autoplay="off"
          captions={false}
        />
      </div>,
    )

    await waitFor(() => {
      expect(ref.current).not.toBeNull()
      expect(typeof ref.current?.focusIndex).toBe('function')
    })

    expect(() => ref.current?.focusIndex(2)).not.toThrow()
    expect(() => ref.current?.focusIndex(-1)).not.toThrow()
    expect(() => ref.current?.focusIndex(99)).not.toThrow()
  })
})

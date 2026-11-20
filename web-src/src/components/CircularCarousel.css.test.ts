import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const cssPath = join(
  dirname(fileURLToPath(import.meta.url)),
  'CircularCarousel.css',
)
const css = readFileSync(cssPath, 'utf8')

describe('CircularCarousel.css theme contrast', () => {
  it('forces dark card names on white theme', () => {
    expect(css).toMatch(
      /html\[data-theme='white']\s+\.circular-carousel__card-name/,
    )
    expect(css).toMatch(/color:\s*#141414/)
  })

  it('uses theme foreground token for default card names', () => {
    expect(css).toMatch(
      /\.circular-carousel__card-name\s*\{[^}]*color:\s*var\(--theme-fg/s,
    )
  })
})

import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const cssPath = join(dirname(fileURLToPath(import.meta.url)), 'themes.css')
const css = readFileSync(cssPath, 'utf8')

describe('themes.css grainient contrast', () => {
  it('uses mix-blend-mode difference for grainient text', () => {
    expect(css).toMatch(/mix-blend-mode:\s*difference/)
    expect(css).toContain("html[data-theme='grainient']")
    expect(css).toContain("html[data-theme='grainient-teal']")
    expect(css).toContain("html[data-theme='grainient-mono']")
  })

  it('keeps app-theme-bg behind content without shell z-index isolation', () => {
    expect(css).toMatch(/\.app-theme-bg\s*\{[^}]*z-index:\s*-1/s)
    // .app-shell must not reintroduce a stacking context via z-index
    const shellBlock = css.match(/\.app-shell\s*\{[^}]+\}/)?.[0] ?? ''
    expect(shellBlock).not.toMatch(/z-index\s*:/)
  })

  it('isolates media so logos are not inverted', () => {
    expect(css).toMatch(/\.org-detail__logo-wrap/)
    expect(css).toMatch(/isolation:\s*isolate/)
  })
})

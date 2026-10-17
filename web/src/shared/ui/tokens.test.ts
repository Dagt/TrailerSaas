import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

// Vitest stubs CSS modules (including ?raw); read the actual token source.
const tokens = readFileSync('src/shared/ui/tokens.css', 'utf8')

function color(name: string) {
  const hex = tokens.match(new RegExp(`--${name}: (#[0-9a-f]{6});`))?.[1]
  if (!hex) throw new Error(`Missing color: ${name}`)
  return hex
}
function luminance(hex: string) {
  const values = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255).map(v => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4)
  return values.reduce((sum, value, i) => sum + value * ([.2126, .7152, .0722][i] ?? 0), 0)
}
function ratio(a: string, b: string) { const x = luminance(color(a)), y = luminance(color(b)); return (Math.max(x, y) + .05) / (Math.min(x, y) + .05) }

describe('measured Fleet Simple contrast', () => {
  const pairs = [
    ['color-text', 'color-surface'], ['color-text-secondary', 'color-surface'], ['color-text-secondary', 'color-canvas'],
    ['color-on-primary', 'color-primary'], ['color-on-primary', 'color-primary-hover'], ['color-on-primary', 'color-accent'],
    ['color-sidebar-active-text', 'color-sidebar-active-bg'], ['color-disabled-text', 'color-disabled-bg'],
    ...['neutral', 'success', 'attention', 'danger', 'unknown', 'pending'].map(tone => [`status-${tone}-text`, `status-${tone}-bg`]),
  ]
  it.each(pairs)('%s on %s passes normal-text AA', (fg, bg) => { expect(ratio(fg, bg)).toBeGreaterThanOrEqual(4.5) })
  it.each(['color-surface', 'color-canvas', 'color-sidebar', 'color-sidebar-active-bg'])('focus against %s is distinguishable', bg => { expect(ratio('color-focus', bg)).toBeGreaterThanOrEqual(3) })
  it('functional control border passes non-text contrast on white', () => { expect(ratio('color-control-border', 'color-surface')).toBeGreaterThanOrEqual(3) })
  it.each(['neutral', 'success', 'attention', 'danger', 'unknown', 'pending'])('%s indicator border passes non-text contrast', tone => { expect(ratio(`status-${tone}-border`, `status-${tone}-bg`)).toBeGreaterThanOrEqual(3) })
})

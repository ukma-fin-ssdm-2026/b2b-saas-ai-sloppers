/** True when the trimmed name starts with a Latin letter (A–Z). */
export function startsWithLatin(name: string): boolean {
  return /^[A-Za-z]/.test(name.trim())
}

export type Named = { name: string }

/**
 * English names first (A–Z), then Ukrainian / other scripts.
 * Within each group: locale-aware alphabetical order.
 */
export function compareOrgNames(a: Named, b: Named): number {
  const an = a.name.trim()
  const bn = b.name.trim()
  const aLatin = startsWithLatin(an)
  const bLatin = startsWithLatin(bn)
  if (aLatin !== bLatin) return aLatin ? -1 : 1
  return an.localeCompare(bn, aLatin ? 'en' : 'uk', { sensitivity: 'base' })
}

export function sortOrganizationsByName<T extends Named>(items: T[]): T[] {
  return [...items].sort(compareOrgNames)
}

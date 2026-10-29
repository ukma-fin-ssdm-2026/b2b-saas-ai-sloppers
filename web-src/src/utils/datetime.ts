const KYIV_FORMATTER = new Intl.DateTimeFormat('uk-UA', {
  timeZone: 'Europe/Kyiv',
  weekday: 'short',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatEventDateTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return KYIV_FORMATTER.format(date)
}

export function dateInputToFromParam(ymd: string): string {
  return `${ymd}T00:00:00Z`
}

export function dateInputToToParam(ymd: string): string {
  const date = new Date(`${ymd}T00:00:00.000Z`)
  date.setUTCDate(date.getUTCDate() + 1)
  return date.toISOString().replace(/\.\d{3}Z$/, 'Z')
}

export function toDatetimeLocalValue(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

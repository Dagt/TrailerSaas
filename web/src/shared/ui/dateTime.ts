/** Display only: US English and the browser's actual local offset, never a payload conversion. */
export function displayTime(value: string): string {
  const instant = new Date(value)
  if (Number.isNaN(instant.valueOf())) return value
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: 'numeric', minute: '2-digit', second: '2-digit', timeZoneName: 'shortOffset',
  }).format(instant)
}

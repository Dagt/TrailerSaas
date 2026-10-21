export function human(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/^./, c => c.toUpperCase()) }
export function when(value: string | null) { return value ? displayTime(value) : 'Not scheduled' }
export function reference(id: string) { return `Trip ${id.slice(0, 8)}` }
import { displayTime } from '../../shared/ui/dateTime'


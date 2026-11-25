import type { TicketSummary } from '../tickets/use-tickets.ts'
import type { ToolTickets } from './use-chat-stream.ts'

/** A ticket reference in an answer's text, such as RET-1046. */
export const REFERENCE = /\bRET-\d+\b/g

/** Each reference the text names, once, in the order it first names them. */
function namedReferences(text: string): string[] {
  return [...new Set(text.match(REFERENCE))]
}

/**
 * The tickets an answer names that a tool returned for it, in the order the
 * answer first names them. A ticket two tools returned reads as the later one.
 */
export function namedTickets(answer: string, found: ToolTickets[]): TicketSummary[] {
  const byReference = new Map(
    found.flatMap((event) => event.tickets).map((ticket) => [ticket.reference, ticket]),
  )
  return namedReferences(answer).flatMap((reference) => byReference.get(reference) ?? [])
}

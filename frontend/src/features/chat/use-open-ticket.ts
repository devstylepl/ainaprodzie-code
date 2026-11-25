import { useSyncExternalStore } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useMatch } from 'react-router'
import { ticketKeys, type TicketDetails, type TicketSummary } from '../tickets/use-tickets.ts'

/**
 * The ticket open on the page beside the chat (chat ADR 0013), once the ticket
 * page has loaded it. The chat reads the page's cached ticket without a query
 * of its own, so it never fetches, and a ticket that is loading, missing or not
 * visible names nothing.
 */
export function useOpenTicket(): TicketSummary | undefined {
  const reference = useMatch('/tickets/:reference')?.params.reference
  const queryClient = useQueryClient()
  return useSyncExternalStore(
    (onChange) => queryClient.getQueryCache().subscribe(onChange),
    () =>
      reference === undefined
        ? undefined
        : queryClient.getQueryData<TicketDetails>(ticketKeys.detail(reference))?.summary,
  )
}

import type { components } from '../../api/types.gen.ts'
import { filtersSearch } from '../tickets/ticket-list-filters.ts'

type ChatTicketListQuery = components['schemas']['ChatTicketListQuery']

/**
 * The ticket list's address for a search's `GET /api/tickets` query, or none
 * when the list cannot repeat it exactly: it takes one category and one
 * priority, and has no author, date or deadline filter (chat ADR 0010). The
 * search's order always goes along, as support's list opens on another one.
 */
export function listAddress(query: ChatTicketListQuery | null): string | undefined {
  if (
    !query ||
    query.category.length > 1 ||
    query.priority.length > 1 ||
    query.authorId !== null ||
    query.createdFrom !== null ||
    query.createdTo !== null ||
    query.deadlineBefore !== null
  ) {
    return undefined
  }
  const search = filtersSearch({
    text: query.text ?? undefined,
    status: query.status,
    category: query.category[0],
    priority: query.priority[0],
    organizationId: query.organizationId ?? undefined,
    teamId: query.teamId ?? undefined,
    assigneeId: query.assigneeId ?? undefined,
    slaBreached: query.slaBreached ?? undefined,
    sort: query.sort,
    page: 0,
  }).toString()
  return search === '' ? '/' : `/?${search}`
}

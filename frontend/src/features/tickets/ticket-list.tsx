import { useEffect, useRef, type ReactNode } from 'react'
import { Box, Button, Flex, Link, Text, VisuallyHidden } from '@chakra-ui/react'
import { Link as RouterLink, useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { Chip } from '../../components/ui/chip.tsx'
import type { LampTone } from '../../components/ui/lamp.tsx'
import {
  DataTable,
  TABLE_FITS,
  TABLE_ROOMY,
  TABLE_TWO_LINES,
  type DataTableColumn,
  type StackedLayout,
} from '../../components/ui/data-table.tsx'
import { fallbackErrorKey } from '../../api/errors.ts'
import { ProblemText } from '../../components/ui/problem-text.tsx'
import { WindowPanel } from '../../components/ui/window-panel.tsx'
import { activeLanguage } from '../../i18n/index.ts'
import { useSession } from '../auth/use-auth.ts'
import { DeskSummary } from './desk-summary.tsx'
import { REFERENCE_LINK_STYLE } from './reference-style.ts'
import { ResponseCell } from './response-cell.tsx'
import { FILTERS_TOGGLE_ID, SEARCH_ID, TicketFilterBar } from './ticket-filter-bar.tsx'
import {
  label,
  priorityEmphasis,
  PRIORITY_KEYS,
  SORT_KEYS,
  STATUS_KEYS,
  useNow,
} from './ticket-labels.ts'
import {
  defaultSort,
  filtersSearch,
  listParams,
  NO_FILTERS,
  parseFilters,
  type ListOrigin,
  type TicketListFilters,
} from './ticket-list-filters.ts'
import { useActiveFilters } from './use-active-filters.ts'
import {
  useCustomerTicketCounts,
  useTicketCounts,
  useTickets,
  type Attention,
  type TicketCounts,
  type TicketSummary,
} from './use-tickets.ts'

const PAGE_SIZE = 20
// The queue keeps its clocks and breaches current while it is on screen.
const REFRESH_MS = 60_000

interface Row {
  id: string
  reference: ReactNode
  title: ReactNode
  organization: ReactNode
  assignee: ReactNode
  status: ReactNode
  priority: ReactNode
  response: ReactNode
}

export function TicketList() {
  const { t } = useTranslation('common')
  const session = useSession().data
  const userId = session?.id ?? ''
  const isSupport = session?.roles.includes('SUPPORT') === true
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = parseFilters(searchParams, isSupport, userId)
  const { page } = filters
  const tickets = useTickets(listParams(filters, isSupport, PAGE_SIZE), {
    refetchInterval: REFRESH_MS,
  })
  const counts = useTicketCounts({ enabled: isSupport, refetchInterval: REFRESH_MS })
  const customerCounts = useCustomerTicketCounts({
    enabled: session !== undefined && !isSupport,
    refetchInterval: REFRESH_MS,
  })
  const now = useNow()
  const activeFilters = useActiveFilters(filters, userId, isSupport)

  // Any change but paging starts again from the first page; a pick that
  // changes nothing keeps the page. The entry is replaced, so browser back
  // leaves the list rather than undoing a filter.
  const apply = (patch: Partial<TicketListFilters>) => {
    const same =
      filtersSearch({ ...filters, ...patch }).toString() === filtersSearch(filters).toString()
    if (same) return
    setSearchParams(filtersSearch({ ...filters, page: 0, ...patch }), { replace: true })
  }

  const columns: DataTableColumn<Row>[] = [
    { key: 'reference', header: t('tickets.columns.reference'), width: '120px' },
    { key: 'title', header: t('tickets.columns.title') },
    ...(isSupport
      ? [
          {
            key: 'organization' as const,
            header: t('tickets.columns.organization'),
            width: '130px',
            // Short of `TABLE_ROOMY` the title keeps its room by carrying the organization under it.
            wideOnly: true,
          },
          // A support name or "Nieprzypisane" fits; a longer name truncates.
          { key: 'assignee' as const, header: t('tickets.columns.assignee'), width: '120px' },
        ]
      : []),
    {
      key: 'status',
      header: t('tickets.columns.status'),
      // Wide enough, between the cell's padding, for the longest stamp, "WAITING FOR CUSTOMER"
      // (159px), or for a customer's "Needs your answer" (124px), which has no stamp.
      width: isSupport ? '190px' : '160px',
      stackedAlign: isSupport ? 'end' : 'start',
    },
    {
      key: 'priority',
      header: t('tickets.columns.priority'),
      width: '110px',
      stackedLabel: true,
      stackedAlign: 'end',
    },
    {
      key: 'response',
      header: t('tickets.columns.response'),
      // Wide enough for a live breach's chip beside its clock on one line, in
      // Polish too ("SLA 23 h 59 min po terminie"). A customer's clock is a
      // longer sentence ("Czeka na zespół wsparcia od 23 godzin"), and their table
      // has no organization or assignee columns to give the room up.
      width: isSupport ? '220px' : '300px',
      stackedAlign: 'end',
    },
  ]
  // Where the table does not fit, a row reads as the reference with its clock, the title, then for
  // support staff the organization with the status and the assignee with the
  // priority; a customer's row has neither, so status and priority share a line,
  // the labelled priority on the trailing edge where the wide column gives it room.
  // The trailing column takes at most 60% of the row, where the clock wraps its
  // own words, and the leading one truncates.
  // From `TABLE_TWO_LINES` (half a desktop screen, or the list beside the docked
  // chat) a row takes two lines: the reference, the title and the clock, then
  // the rest under the title in tracks wide enough for the longest status chip
  // and a semibold "Priorytet Krytyczny", so the values line up down the list
  // as columns.
  const stacked: StackedLayout = {
    templateColumns: 'minmax(0, 1fr) fit-content(60%)',
    templateAreas: isSupport
      ? `"reference response" "title title" "organization status" "assignee priority"`
      : `"reference response" "title title" "status priority"`,
    twoLines: isSupport
      ? {
          templateColumns: '100px minmax(0, 1fr) 120px 160px 140px',
          templateAreas: `"reference title title response response" ". organization assignee status priority"`,
        }
      : {
          templateColumns: '100px minmax(0, 1fr) 300px',
          templateAreas: `"reference title response" ". status priority"`,
        },
  }
  const rows = (tickets.data?.items ?? []).map((ticket) => toRow(ticket))
  const total = tickets.data?.total ?? 0
  const from = total === 0 ? 0 : page * PAGE_SIZE + 1
  const to = Math.min(total, (page + 1) * PAGE_SIZE)
  const lastPage = Math.max(0, Math.ceil(total / PAGE_SIZE) - 1)
  // A page past the end, left behind by tickets that no longer match or by an
  // old link, moves to the last page instead of showing an empty table.
  const pastTheEnd = tickets.data !== undefined && !tickets.isPlaceholderData && page > lastPage
  useEffect(() => {
    if (pastTheEnd) apply({ page: lastPage })
  })

  // The next page starts at the table's top, so a reader who paged from the
  // bottom is brought back up to it, 12px clear of the edge. The focus stays on
  // the pager: on the first or last page the button just pressed is disabled,
  // which would drop the focus to the body, so it passes to the other one.
  const list = useRef<HTMLDivElement>(null)
  const previousButton = useRef<HTMLButtonElement>(null)
  const nextButton = useRef<HTMLButtonElement>(null)
  const turnPage = (to: number) => {
    apply({ page: to })
    if (to === 0) nextButton.current?.focus()
    if (to === lastPage) previousButton.current?.focus()
    const top = list.current?.querySelector('[role="table"]')?.getBoundingClientRect().top
    if (top !== undefined && top < 0) window.scrollBy({ top: top - 12 })
  }

  function toRow(ticket: TicketSummary): Row {
    return {
      id: ticket.reference,
      reference: (
        <Link
          asChild
          {...REFERENCE_LINK_STYLE}
          // A stacked row is one tap target: the link stretches over it.
          css={{
            _before: { content: '""', position: 'absolute', inset: '0' },
            [TABLE_FITS]: { _before: { display: 'none' } },
          }}
        >
          <RouterLink
            to={`/tickets/${ticket.reference}`}
            state={{ listSearch: searchParams.toString() } satisfies ListOrigin}
          >
            {ticket.reference}
          </RouterLink>
        </Link>
      ),
      title: (
        <>
          {/* Two stacked lines end on more than one word where the browser can manage it. */}
          <Text
            overflowWrap="anywhere"
            textWrap="pretty"
            css={{ lineClamp: 2, [TABLE_TWO_LINES]: { lineClamp: 1 } }}
          >
            {ticket.priority === 'CRITICAL' && <CriticalMarker />}
            {ticket.title}
          </Text>
          {isSupport && (
            <Text
              truncate
              color="fg.muted"
              fontSize="13px"
              css={{
                display: 'none',
                [TABLE_FITS]: { display: 'block' },
                [TABLE_ROOMY]: { display: 'none' },
              }}
            >
              {ticket.organization.name}
            </Text>
          )}
        </>
      ),
      organization: <Text truncate>{ticket.organization.name}</Text>,
      assignee: (
        <Text truncate color={ticket.assignee ? 'fg' : 'fg.muted'}>
          {ticket.assignee?.name ?? t('tickets.unassigned')}
        </Text>
      ),
      // A customer is told in words which tickets wait for them; support staff read the status.
      status:
        !isSupport && ticket.status === 'WAITING_FOR_CUSTOMER' ? (
          <Text fontWeight="semibold">{t('tickets.needsYourAnswer')}</Text>
        ) : (
          <Chip>{label(t, STATUS_KEYS, ticket.status)}</Chip>
        ),
      priority: (
        <Text {...priorityEmphasis(ticket.priority)}>
          {label(t, PRIORITY_KEYS, ticket.priority)}
        </Text>
      ),
      response: <ResponseCell ticket={ticket} now={now} isSupport={isSupport} />,
    }
  }

  // A desk count opens the list narrowed to its tickets alone, in the reader's order.
  const deskSearch = (attention: Attention) =>
    filtersSearch({ ...filters, ...NO_FILTERS, attention, page: 0 }).toString()
  const deskCount =
    filters.attention !== undefined &&
    filtersSearch({ ...filters, page: 0 }).toString() === deskSearch(filters.attention)
      ? filters.attention
      : undefined

  // The empty state names what matched nothing and lifts the search and the
  // filters apart, so the reader keeps whichever part they still want.
  const searched = filters.text !== undefined
  const narrowed = activeFilters.length > 0
  const named = {
    text: filters.text,
    filters: new Intl.ListFormat(activeLanguage(), { type: 'conjunction' }).format(
      activeFilters.map((filter) => filter.label),
    ),
    count: activeFilters.length,
  }
  const emptyHint = searched
    ? t(narrowed ? 'tickets.empty.searchAndFilters' : 'tickets.empty.search', named)
    : narrowed
      ? t('tickets.empty.filters', named)
      : t(isSupport ? 'tickets.empty.none' : 'tickets.empty.noneCustomer')
  // Each clear hands the focus to the control that set what it cleared.
  const clears = [
    ...(searched
      ? [{ label: t('tickets.empty.clearSearch'), patch: { text: undefined }, focus: SEARCH_ID }]
      : []),
    ...(narrowed
      ? [
          {
            label: t('tickets.filters.clear'),
            patch: { ...NO_FILTERS, text: filters.text },
            focus: FILTERS_TOGGLE_ID,
          },
        ]
      : []),
  ]
  const emptyAction =
    clears.length > 0 ? (
      <Flex gap="8px" wrap="wrap" justify="center">
        {clears.map((clear) => (
          <Button
            key={clear.focus}
            variant="outline"
            onClick={() => {
              apply(clear.patch)
              document.getElementById(clear.focus)?.focus()
            }}
          >
            {clear.label}
          </Button>
        ))}
      </Flex>
    ) : undefined

  return (
    <Box ref={list}>
      {/* Above the search and filters: it counts the whole desk, not what they narrow the table to. */}
      {isSupport && <DeskSummary counts={counts.data} current={deskCount} search={deskSearch} />}
      <WindowPanel
        name={label(t, SORT_KEYS, filters.sort ?? defaultSort(isSupport))}
        meta={tickets.data && t('tickets.views.count', { count: total })}
        lamp={isSupport && counts.data ? deskLamp(counts.data) : undefined}
      >
        <Box
          px={{ base: '16px', sm: '20px' }}
          pt="16px"
          pb="4px"
          bg="bg.subtle"
          borderBottomWidth="ink"
          borderColor="border.control"
        >
          <TicketFilterBar
            filters={filters}
            apply={apply}
            userId={userId}
            isSupport={isSupport}
            activeFilters={activeFilters}
            counts={counts.data}
            customerCounts={customerCounts.data}
          />
        </Box>
        {tickets.isPending && (
          <Text role="status" color="fg.muted" textAlign="center" py="36px">
            {t('loading')}
          </Text>
        )}
        {/* A failed load has no data: only the error shows, never the empty state. */}
        {tickets.data && !pastTheEnd && (
          <DataTable
            columns={columns}
            rows={rows}
            getRowKey={(row) => row.id}
            emptyTitle={t('tickets.emptyTitle')}
            emptyHint={emptyHint}
            emptyAction={emptyAction}
            stacked={stacked}
          />
        )}
        {tickets.error && (
          <Box px={{ base: '16px', sm: '20px' }} py="16px">
            <ProblemText
              flush
              error={t(fallbackErrorKey(tickets.error))}
              onRetry={() => void tickets.refetch()}
              retrying={tickets.isFetching}
            />
          </Box>
        )}
        {total > 0 && !pastTheEnd && (
          <Flex
            align="center"
            justify="flex-end"
            gap="12px"
            px={{ base: '16px', sm: '20px' }}
            py="12px"
            bg="bg.subtle"
            borderTopWidth="ink"
            borderColor="border.control"
          >
            <Text
              minW="110px"
              textAlign="right"
              fontFamily="mono"
              fontSize="12px"
              fontWeight="600"
              letterSpacing="0.04em"
            >
              {t('tickets.range', { from, to, total })}
            </Text>
            {/* With every ticket on one page there is nowhere to turn. */}
            {lastPage > 0 && (
              <>
                <Button
                  ref={previousButton}
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => turnPage(page - 1)}
                >
                  {t('tickets.previous')}
                </Button>
                <Button
                  ref={nextButton}
                  variant="outline"
                  size="sm"
                  disabled={to >= total}
                  onClick={() => turnPage(page + 1)}
                >
                  {t('tickets.next')}
                </Button>
              </>
            )}
          </Flex>
        )}
      </WindowPanel>
    </Box>
  )
}

/** The queue's lamp: red while a breach waits unanswered, amber while a deadline or a customer is close. */
function deskLamp(counts: TicketCounts): LampTone {
  if (counts.pastDeadline > 0) return 'red'
  if (counts.dueWithinHour > 0 || counts.unansweredCustomerComment > 0) return 'amber'
  return 'green'
}

/**
 * Critical leads its title, the one priority marked where the eye starts a
 * row, in the stacked layout too. Its word and the filled ink stamp carry it,
 * not a colour: red stays the breach's.
 */
function CriticalMarker() {
  const { t } = useTranslation('common')
  return (
    <Box as="span" data-priority="CRITICAL" me="6px">
      <Box as="span" aria-hidden="true">
        <Chip tone="ink">{label(t, PRIORITY_KEYS, 'CRITICAL')}</Chip>
      </Box>
      <VisuallyHidden>{`${t('tickets.criticalMarker')}: `}</VisuallyHidden>
    </Box>
  )
}

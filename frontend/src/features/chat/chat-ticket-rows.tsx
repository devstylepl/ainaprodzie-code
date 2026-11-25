import { useId, useState } from 'react'
import { Box, Button, Grid, HStack, Link, Text } from '@chakra-ui/react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router'
import { Chip } from '../../components/ui/chip.tsx'
import { useSession } from '../auth/use-auth.ts'
import { REFERENCE_LINK_STYLE } from '../tickets/reference-style.ts'
import { ResponseCell } from '../tickets/response-cell.tsx'
import { label, STATUS_KEYS, useNow } from '../tickets/ticket-labels.ts'
import type { TicketSummary } from '../tickets/use-tickets.ts'
import { riseIn } from './chat-motion.ts'

/** Rows shown before "Show all". */
const SHOWN = 5

/**
 * The tickets under an answer, drawn as the ticket list draws them from what
 * the tools returned, never from what the model wrote (chat ADR 0010). They are
 * a snapshot of when the question was asked; each links to the live ticket.
 */
export function ChatTicketRows(props: { tickets: TicketSummary[] }) {
  const { t } = useTranslation('chat')
  const [all, setAll] = useState(false)
  const listId = useId()
  const now = useNow()
  const isSupport = useSession().data?.roles.includes('SUPPORT') === true
  if (props.tickets.length === 0) return null
  const shown = all ? props.tickets : props.tickets.slice(0, SHOWN)

  return (
    <Box>
      <Box
        as="ul"
        id={listId}
        aria-label={t('answerTickets.label')}
        listStyleType="none"
        borderTopWidth="ink"
        borderColor="border.control"
        fontSize="13px"
      >
        {shown.map((ticket, index) => (
          <TicketRow
            key={ticket.reference}
            ticket={ticket}
            now={now}
            isSupport={isSupport}
            delay={index * 40}
          />
        ))}
      </Box>
      {props.tickets.length > SHOWN && (
        <Button
          variant="ghost"
          size="xs"
          mt="6px"
          aria-expanded={all}
          aria-controls={listId}
          onClick={() => setAll(!all)}
        >
          {all
            ? t('answerTickets.showFewer')
            : t('answerTickets.showAll', { count: props.tickets.length })}
        </Button>
      )}
    </Box>
  )
}

/**
 * The reference with its status and the list's own Response cell, so the clock
 * reads as it does in the list, then the title on a line of its own, so Polish
 * labels fit at 400px.
 */
function TicketRow(props: {
  ticket: TicketSummary
  now: number
  isSupport: boolean
  delay: number
}) {
  const { t } = useTranslation('common')
  const { ticket } = props
  return (
    <Grid
      as="li"
      position="relative"
      templateColumns="auto minmax(0, 1fr)"
      templateAreas={`"reference facts" "title title"`}
      columnGap="8px"
      rowGap="2px"
      py="8px"
      borderBottomWidth="1px"
      borderBottomStyle="dashed"
      borderColor="border"
      {...riseIn(props.delay)}
    >
      <Link
        gridArea="reference"
        asChild
        {...REFERENCE_LINK_STYLE}
        // The row is one tap target: the link stretches over it.
        css={{ _before: { content: '""', position: 'absolute', inset: '0' } }}
      >
        <RouterLink
          to={`/tickets/${ticket.reference}`}
          aria-label={`${ticket.reference} ${ticket.title}`}
        >
          {ticket.reference}
        </RouterLink>
      </Link>
      {/* Where the stamp and the clock do not share the line, the clock moves under the
          stamp whole rather than breaking between a number and its unit. */}
      <HStack
        gridArea="facts"
        justify="flex-end"
        align="baseline"
        wrap="wrap"
        columnGap="6px"
        rowGap="2px"
        color="fg.muted"
      >
        <Chip>{label(t, STATUS_KEYS, ticket.status)}</Chip>
        <Box flexShrink="0" maxW="full">
          <ResponseCell ticket={ticket} now={props.now} isSupport={props.isSupport} />
        </Box>
      </HStack>
      <Text gridArea="title" truncate>
        {ticket.title}
      </Text>
    </Grid>
  )
}

import { Box, HStack, Text, VisuallyHidden } from '@chakra-ui/react'
import { useTranslation } from 'react-i18next'
import { Chip } from '../../components/ui/chip.tsx'
import { Lamp, type LampTone } from '../../components/ui/lamp.tsx'
import { TABLE_TWO_LINES } from '../../components/ui/data-table.tsx'
import {
  clockEmphasis,
  clockLamp,
  clockText,
  customerClockEmphasis,
  customerClockText,
  customerCommentText,
  responseClock,
} from './ticket-labels.ts'
import type { TicketSummary } from './use-tickets.ts'

/**
 * Where the ticket stands against its response deadline: the danger chip for
 * a live breach (still awaiting a first response past the deadline), then the
 * clock. Red marks only what needs a person now: a breach already settled
 * (answered or closed late) says so in the muted clock, and a screen reader
 * hears that clock's full sentence instead of its short form. The chip stays
 * beside the clock; a clock too long for its column wraps its own words rather
 * than dropping under the chip. Once the first response has settled the
 * clock, an unanswered customer comment takes its place in semibold Ink, as
 * support's next piece of work, never in red. A customer reads how long the
 * ticket has waited for support instead, with no chip and no deadline.
 */
export function ResponseCell(props: { ticket: TicketSummary; now: number; isSupport: boolean }) {
  const { t } = useTranslation('common')
  const customerComment = props.isSupport ? customerCommentText(t, props.ticket, props.now) : null
  if (customerComment !== null) {
    return (
      <Text
        data-clock="customerComment"
        fontWeight="semibold"
        fontVariantNumeric="tabular-nums"
        css={{ textAlign: 'end', [TABLE_TWO_LINES]: { textAlign: 'start' } }}
      >
        <InlineLamp tone="amber" />
        {customerComment}
      </Text>
    )
  }
  const clock = responseClock(props.ticket, props.now)
  const soon = clock.kind === 'due' && clock.soon
  const pastBreach = clock.kind === 'answeredLate' || clock.kind === 'closedLate'
  const customerClock = props.isSupport ? null : customerClockText(t, props.ticket, props.now)
  return (
    <HStack
      gap="8px"
      align="baseline"
      css={{ justifyContent: 'flex-end', [TABLE_TWO_LINES]: { justifyContent: 'flex-start' } }}
    >
      {props.isSupport && clock.kind === 'over' && (
        <Box as="span" flexShrink="0">
          <Box as="span" aria-hidden="true" title={t('tickets.slaBreached')}>
            <Chip tone="danger">{t('tickets.filters.sla')}</Chip>
          </Box>
          <VisuallyHidden>{t('tickets.slaBreached')}</VisuallyHidden>
        </Box>
      )}
      {props.isSupport ? (
        <Text
          data-clock={clock.kind}
          data-soon={soon || undefined}
          {...clockEmphasis(clock)}
          fontVariantNumeric="tabular-nums"
          css={{ textAlign: 'end', [TABLE_TWO_LINES]: { textAlign: 'start' } }}
        >
          {clock.kind !== 'over' && <InlineLamp tone={clockLamp(clock)} />}
          {pastBreach ? (
            <>
              <span aria-hidden="true">{clockText(t, clock, 'short')}</span>
              <VisuallyHidden>{clockText(t, clock, 'long')}</VisuallyHidden>
            </>
          ) : (
            clockText(t, clock, 'short')
          )}
        </Text>
      ) : (
        customerClock !== null && (
          <Text
            {...customerClockEmphasis(props.ticket)}
            fontVariantNumeric="tabular-nums"
            css={{ textAlign: 'end', [TABLE_TWO_LINES]: { textAlign: 'start' } }}
          >
            {customerClock}
          </Text>
        )
      )}
    </HStack>
  )
}

/** A lamp set in the clock's first line, so it stays beside the words when they wrap. */
function InlineLamp(props: { tone: LampTone }) {
  return (
    <Box as="span" me="8px">
      <Lamp tone={props.tone} />
    </Box>
  )
}

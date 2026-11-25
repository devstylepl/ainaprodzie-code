import { CloseButton, Flex, Link, Text } from '@chakra-ui/react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router'
import { TOUCH_FLOOR } from '../../components/ui/control-style.ts'
import { REFERENCE_LINK_STYLE } from '../tickets/reference-style.ts'
import type { TicketSummary } from '../tickets/use-tickets.ts'
import { riseIn } from './chat-motion.ts'

/**
 * The ticket the next question is about, above the question box: its reference
 * links to the ticket, and the button removes it for the questions that follow.
 */
export function ChatTicketContext(props: {
  ticket: TicketSummary
  onDismiss: () => void
  onFollowLink: () => void
}) {
  const { t } = useTranslation('chat')
  const { reference, title } = props.ticket
  return (
    <Flex
      role="group"
      aria-label={t('ticketContext.label', { reference })}
      align="center"
      gap="6px"
      mb="8px"
      fontSize="13px"
      {...riseIn()}
    >
      <Link asChild {...REFERENCE_LINK_STYLE} flexShrink="0">
        <RouterLink to={`/tickets/${reference}`} onClick={props.onFollowLink}>
          {reference}
        </RouterLink>
      </Link>
      <Text aria-hidden="true" color="fg.muted">
        ·
      </Text>
      <Text truncate flex="1" minW="0" color="fg.muted">
        {title}
      </Text>
      <CloseButton
        size="2xs"
        css={TOUCH_FLOOR}
        aria-label={t('ticketContext.dismiss', { reference })}
        onClick={props.onDismiss}
      />
    </Flex>
  )
}

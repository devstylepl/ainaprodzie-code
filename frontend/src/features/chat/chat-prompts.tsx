import { Flex, Text } from '@chakra-ui/react'
import { useTranslation } from 'react-i18next'
import { EmptyState } from '../../components/ui/empty-state.tsx'
import { SuggestionButton } from '../../components/ui/suggestion-button.tsx'
import type { SessionUser } from '../auth/use-auth.ts'
import { riseIn } from './chat-motion.ts'

type Role = SessionUser['roles'][number]

const PROMPT_KEYS: Record<Role, string[]> = {
  USER: ['prompts.USER.waiting', 'prompts.USER.oldest', 'prompts.USER.resolved'],
  SUPPORT: ['prompts.SUPPORT.breached', 'prompts.SUPPORT.assigned', 'prompts.SUPPORT.organization'],
}

/** Questions about the ticket open beside the chat, which the question is sent with. */
const TICKET_PROMPT_KEYS: Record<Role, string[]> = {
  USER: ['ticketPrompts.USER.status'],
  SUPPORT: ['ticketPrompts.SUPPORT.summary', 'ticketPrompts.SUPPORT.waiting'],
}

/**
 * The empty conversation: what the chat is for, and questions the reader's
 * role would ask, about the open ticket when there is one.
 */
export function ChatPrompts(props: {
  roles: Role[]
  onTicket: boolean
  onAsk: (question: string) => void
}) {
  const { t } = useTranslation('chat')
  const prompts = props.onTicket ? TICKET_PROMPT_KEYS : PROMPT_KEYS
  // A role newer than this bundle has no prompts rather than another role's.
  const keys = props.roles.flatMap(
    (role) => (prompts as Record<string, string[] | undefined>)[role] ?? [],
  )
  return (
    <EmptyState title={t('empty.title')} titleAs="h3">
      <Text fontSize="14px">{t('empty.hint')}</Text>
      {/* 10px apart, so each button's 3px shadow keeps clear of the next. */}
      <Flex direction="column" gap="10px" mt="16px" w="full">
        {keys.map((key, index) => (
          <SuggestionButton
            key={key}
            {...riseIn(120 + index * 60)}
            onClick={() => props.onAsk(t(key))}
          >
            {t(key)}
          </SuggestionButton>
        ))}
      </Flex>
    </EmptyState>
  )
}

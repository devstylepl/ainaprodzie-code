import { useEffect, useRef, useState, type KeyboardEvent, type MouseEvent } from 'react'
import { Box, Button, CloseButton, Flex, Heading, VisuallyHidden } from '@chakra-ui/react'
import { useTranslation } from 'react-i18next'
import { TOUCH_FLOOR } from '../../components/ui/control-style.ts'
import { Lamp } from '../../components/ui/lamp.tsx'
import { useSession } from '../auth/use-auth.ts'
import { ChatComposer } from './chat-composer.tsx'
import { panelIn } from './chat-motion.ts'
import { ChatMessageList } from './chat-message-list.tsx'
import { ChatPrompts } from './chat-prompts.tsx'
import { ChatTicketContext } from './chat-ticket-context.tsx'
import { useChatStream } from './use-chat-stream.ts'
import { useOpenTicket } from './use-open-ticket.ts'

export const CHAT_PANEL_ID = 'chat-panel'
const TITLE_ID = 'chat-panel-title'

/**
 * The chat docked in the shell (chat ADR 0006): beside the main column on `lg`
 * and wider, a full-width layer under the header below that. The shell keeps it
 * mounted while closed, so the conversation survives navigation, and it sends
 * nothing before the first question. `data-state` is the conversation's state:
 * `empty`, or the last turn's `streaming`, `answered`, `stopped` or `error`.
 */
export function ChatPanel(props: { open: boolean; onClose: () => void; onFollowLink: () => void }) {
  const { t } = useTranslation('chat')
  const roles = useSession().data?.roles ?? []
  const chat = useChatStream()
  const openTicket = useOpenTicket()
  const [dismissed, setDismissed] = useState<string>()
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const scrollRef = useRef<HTMLDivElement>(null)
  const contentRef = useRef<HTMLDivElement>(null)
  const following = useRef(true)

  useEffect(() => {
    if (props.open) inputRef.current?.focus()
  }, [props.open])

  // The conversation follows its growing end (a new question, steps, the answer
  // flowing in) while the reader is at the end; scrolled up, they are left there.
  useEffect(() => {
    const scroller = scrollRef.current
    const content = contentRef.current
    if (!scroller || !content) return
    const observer = new ResizeObserver(() => {
      if (following.current) scroller.scrollTop = scroller.scrollHeight
    })
    observer.observe(content)
    return () => observer.disconnect()
  }, [])

  const onScroll = () => {
    const scroller = scrollRef.current
    if (scroller) {
      following.current = scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight < 48
    }
  }

  // A removed ticket stays removed while it is open; leaving it forgets the
  // removal, so the next ticket opened, even the same one, is named again.
  if (dismissed !== undefined && dismissed !== openTicket?.reference) setDismissed(undefined)
  const ticket = openTicket?.reference === dismissed ? undefined : openTicket

  const dismissTicket = () => {
    setDismissed(ticket?.reference)
    inputRef.current?.focus()
  }

  // A suggested question unmounts the button that asked it, so focus moves to
  // the box the next question goes into instead of falling to the body.
  const ask = (question: string) => {
    following.current = true
    chat.send(question, ticket?.reference)
    inputRef.current?.focus()
  }

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && !event.defaultPrevented && !event.nativeEvent.isComposing) {
      props.onClose()
    }
  }

  const onNewConversation = () => {
    chat.reset()
    inputRef.current?.focus()
  }

  // Every link in the conversation is a ticket link the answer renderer made.
  const onClick = (event: MouseEvent) => {
    if (event.target instanceof Element && event.target.closest('a')) props.onFollowLink()
  }

  return (
    <Flex
      as="aside"
      id={CHAT_PANEL_ID}
      aria-labelledby={TITLE_ID}
      data-state={chat.turns.at(-1)?.status ?? 'empty'}
      display={props.open ? 'flex' : 'none'}
      direction="column"
      // The shell renders the panel before the page, so Tab reaches it straight
      // from its toggle; `order` still draws it after the page on `lg`.
      order={{ lg: 1 }}
      position={{ base: 'absolute', lg: 'static' }}
      inset="0"
      zIndex={{ base: '1', lg: 'auto' }}
      w={{ base: 'full', lg: '400px' }}
      flexShrink="0"
      bg="bg.panel"
      borderStartWidth={{ base: '0', lg: 'ink' }}
      borderColor="border.control"
      onKeyDown={onKeyDown}
      {...panelIn}
    >
      {/* The course's window bar: three dark lamps, as no clock is the chat's to light. */}
      <Flex
        align="center"
        gap="12px"
        flexShrink="0"
        bg="bg.subtle"
        borderBottomWidth="ink"
        borderColor="border.control"
        ps="16px"
        pe="8px"
        py="8px"
      >
        <Flex as="span" gap="4px" aria-hidden="true">
          <Lamp tone="off" size="10px" />
          <Lamp tone="off" size="10px" />
          <Lamp tone="off" size="10px" />
        </Flex>
        <Heading
          as="h2"
          id={TITLE_ID}
          flex="1"
          fontSize="26px"
          lineHeight="1"
          fontWeight="normal"
          textTransform="uppercase"
          letterSpacing="0.01em"
        >
          {t('title')}
        </Heading>
        <Button
          type="button"
          variant="ghost"
          size="xs"
          disabled={chat.turns.length === 0}
          onClick={onNewConversation}
        >
          {t('newConversation')}
        </Button>
        <CloseButton size="sm" css={TOUCH_FLOOR} aria-label={t('close')} onClick={props.onClose} />
      </Flex>
      {/* Positioned, so a visually hidden speaker label scrolls with it rather than stretching the page. */}
      <Box
        ref={scrollRef}
        flex="1"
        minH="0"
        overflowY="auto"
        position="relative"
        px="16px"
        py="14px"
        onClick={onClick}
        onScroll={onScroll}
      >
        <Box ref={contentRef}>
          {chat.turns.length === 0 && (
            <ChatPrompts roles={roles} onTicket={ticket !== undefined} onAsk={ask} />
          )}
          <ChatMessageList turns={chat.turns} onRetry={chat.retry} />
        </Box>
      </Box>
      <ChatComposer
        streaming={chat.streaming}
        onSend={ask}
        onStop={chat.stop}
        inputRef={inputRef}
        context={
          ticket && (
            <ChatTicketContext
              ticket={ticket}
              onDismiss={dismissTicket}
              onFollowLink={props.onFollowLink}
            />
          )
        }
        placeholder={
          ticket
            ? t('composer.ticketPlaceholder', { reference: ticket.reference })
            : t('composer.placeholder')
        }
      />
      {/* What the next question is about, said as the open ticket changes; a region
          of its own, so the row's link and button are not read out with it. */}
      <VisuallyHidden aria-live="polite">
        {ticket && t('ticketContext.label', { reference: ticket.reference })}
      </VisuallyHidden>
    </Flex>
  )
}

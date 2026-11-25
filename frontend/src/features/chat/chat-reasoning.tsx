import { useId, useState } from 'react'
import { Box, Button, Flex, Text } from '@chakra-ui/react'
import { useTranslation } from 'react-i18next'
import { TOUCH_FLOOR } from '../../components/ui/control-style.ts'
import { ChatAnswer } from './chat-answer.tsx'
import { riseIn } from './chat-motion.ts'
import { StepMarker, type StepState } from './chat-trace.tsx'
import type { Turn } from './use-chat-stream.ts'
import { useSmoothText } from './use-smooth-text.ts'

/** The reasoning's whole seconds, at least one, so a fast model never reads as zero. */
const seconds = (startedAt: number, endedAt: number) =>
  Math.max(1, Math.round((endedAt - startedAt) / 1000))

/**
 * The model's reasoning before the answer, as a step of the turn (chat ADR
 * 0020). While the model reasons, the step is "Thinking" over a window of
 * three lines that shows the latest text and keeps its height, so nothing
 * below moves. The first event that is not reasoning folds the window, and the
 * step says how long the model thought, with a button that unfolds the whole
 * text. A turn stopped or failed before its answer keeps what arrived. The
 * step opts out of the conversation's live region, so the reasoning is never
 * announced.
 */
export function ChatReasoning(props: { turn: Turn }) {
  const { t } = useTranslation('chat')
  const [open, setOpen] = useState(false)
  const textId = useId()
  const { turn } = props
  const reasoning = turn.reasoning
  const active = turn.status === 'streaming' && reasoning?.endedAt === undefined
  const flowing = useSmoothText(reasoning?.text ?? '', active)
  if (reasoning === undefined) return null

  const state: StepState = active
    ? 'active'
    : turn.answer === '' && turn.status !== 'answered'
      ? 'interrupted'
      : 'done'
  const label =
    state === 'done' && reasoning.endedAt !== undefined
      ? t('reasoning.thought', { seconds: seconds(reasoning.startedAt, reasoning.endedAt) })
      : t('reasoning.thinking')

  return (
    <Box aria-live="off" fontSize="13px" pb="4px" {...riseIn()}>
      <Flex columnGap="8px" align="center" wrap="wrap">
        <Flex data-step={state} gap="8px">
          <StepMarker state={state} />
          <Text lineHeight="20px" color="fg" fontWeight="semibold">
            {label}
          </Text>
        </Flex>
        {!active && (
          <Button
            variant="ghost"
            size="xs"
            css={TOUCH_FLOOR}
            aria-expanded={open}
            aria-controls={open ? textId : undefined}
            onClick={() => setOpen(!open)}
          >
            {t('reasoning.show')}
          </Button>
        )}
      </Flex>
      {active && (
        // Three lines of the reasoning's 20px, its latest text at the bottom edge.
        <Flex
          aria-hidden="true"
          direction="column"
          justify="flex-end"
          h="60px"
          ms="20px"
          overflow="hidden"
        >
          <Box flexShrink="0">
            <ChatAnswer text={flowing.text} tone="reasoning" compact />
          </Box>
        </Flex>
      )}
      {!active && open && (
        <Box id={textId} ms="20px" mt="4px">
          <ChatAnswer text={reasoning.text} tone="reasoning" />
        </Box>
      )}
    </Box>
  )
}

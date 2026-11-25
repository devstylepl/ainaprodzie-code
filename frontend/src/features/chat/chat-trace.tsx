import { Box, chakra, Flex, Link, Spinner, Text } from '@chakra-ui/react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router'
import { activeLanguage } from '../../i18n/index.ts'
import { drawIn, riseIn, settleIn } from './chat-motion.ts'
import { listAddress } from './list-address.ts'
import { toolStatus, type ToolCall, type ToolStep } from './tool-status.ts'
import type { Turn } from './use-chat-stream.ts'

/**
 * `active` is the step the chat is on now, or a call the model is still
 * writing, `done` one it moved past, and `interrupted` the last step of a turn
 * that stopped or failed before the answer, which may never have finished, or
 * a call the model never finished writing.
 */
export type StepState = 'active' | 'done' | 'interrupted'

interface Step extends ToolStep {
  tool?: string
  state: StepState
  /** The ticket list repeating a search, for one the list can repeat exactly. */
  list?: string
}

/**
 * The work behind an answer, as it happens: one step per tool the model called,
 * in words, what the chat does in Ink and the arguments the model chose muted
 * under it, each settling from a spinner into a drawn check once the chat moves
 * on (chat ADR 0004). A step is there from the moment the model names the tool,
 * where the provider says so, and gets its arguments when the call is complete.
 * Before the first tool call, a single "preparing" step holds the place, so the
 * reader sees that something is happening.
 */
export function ChatTrace(props: { turn: Turn }) {
  const { t } = useTranslation('chat')
  const { t: tCommon } = useTranslation('common')
  const steps = traceSteps(props.turn, (call) =>
    toolStatus(call, { t, tCommon, language: activeLanguage() }),
  )
  // The model's reasoning has a step of its own, which takes the preparing step's place.
  const preparing = steps.length === 0 && waiting(props.turn) && props.turn.reasoning === undefined
  const shown: Step[] = preparing ? [{ action: t('pending'), state: 'active' }] : steps
  if (shown.length === 0) return null

  return (
    // 12px from the answer with the turn's 8px gap, so the steps read as their own block above it.
    <Box as="ul" listStyleType="none" fontSize="13px" pb="4px">
      {shown.map((step, index) => (
        <Flex
          as="li"
          // The preparing step makes way for the first tool call rather than turning into it.
          key={step.tool === undefined ? 'preparing' : index}
          data-tool={step.tool}
          data-step={step.state}
          position="relative"
          gap="8px"
          pb={index < shown.length - 1 ? '8px' : '0'}
          {...riseIn()}
          // The rail joins a step's marker to the next one.
          _before={
            index < shown.length - 1
              ? {
                  content: '""',
                  position: 'absolute',
                  insetStart: '5.5px',
                  top: '17px',
                  bottom: '-3px',
                  w: '1px',
                  bg: 'border',
                }
              : undefined
          }
        >
          <StepMarker state={step.state} />
          <Box minW="0">
            <Text lineHeight="20px" color="fg" fontWeight="semibold" overflowWrap="anywhere">
              {step.action}
            </Text>
            {step.detail && (
              <Text lineHeight="18px" color="fg.muted" overflowWrap="anywhere">
                {step.detail}
              </Text>
            )}
            {step.list && (
              <Link
                asChild
                color="accent.fg"
                fontWeight="semibold"
                textDecoration="underline"
                textDecorationThickness="1.5px"
                textUnderlineOffset="3px"
                {...riseIn()}
              >
                <RouterLink to={step.list}>{t('showInList')}</RouterLink>
              </Link>
            )}
          </Box>
        </Flex>
      ))}
    </Box>
  )
}

/** The 12px marker a step leads with: a spinner, a drawn check or a hollow ring. */
export function StepMarker(props: { state: StepState }) {
  return (
    <Flex
      aria-hidden="true"
      w="12px"
      h="20px"
      flexShrink="0"
      align="center"
      justify="center"
      color="fg"
    >
      <StepMark key={props.state} state={props.state} />
    </Flex>
  )
}

function StepMark(props: { state: StepState }) {
  switch (props.state) {
    case 'active':
      return <Spinner size="xs" color="accent.solid" borderWidth="1.5px" {...settleIn} />
    case 'done':
      return (
        <chakra.svg viewBox="0 0 12 12" w="12px" h="12px" fill="none" {...settleIn}>
          <chakra.path
            d="M2.5 6.25 5 8.75 9.5 3.5"
            pathLength={1}
            stroke="currentColor"
            strokeWidth="1.75"
            strokeLinecap="round"
            strokeLinejoin="round"
            {...drawIn}
          />
        </chakra.svg>
      )
    case 'interrupted':
      return <Box w="7px" h="7px" borderRadius="full" borderWidth="1.5px" borderColor="fg.muted" />
  }
}

/** The turn is still on its way to the first words of the answer. */
const waiting = (turn: Turn) => turn.status === 'streaming' && turn.answer === ''

function traceSteps(turn: Turn, status: (call: ToolCall) => ToolStep): Step[] {
  const last = turn.tools.length - 1
  const lastState: StepState = waiting(turn)
    ? 'active'
    : turn.answer === '' && turn.status !== 'answered'
      ? 'interrupted'
      : 'done'
  const startedState: StepState = turn.status === 'streaming' ? 'active' : 'interrupted'
  return turn.tools.map((call, index) => ({
    tool: call.tool,
    ...status(call.type === 'tool' ? call : { type: 'tool', tool: call.tool, arguments: {} }),
    state: call.type === 'toolStarted' ? startedState : index === last ? lastState : 'done',
    list: listAddress(turn.tickets.find((tickets) => tickets.toolCall === index)?.query ?? null),
  }))
}

import { useEffect, useRef, useState } from 'react'
import { api, requestHeaders } from '../../api/client.ts'
import type { components, paths } from '../../api/types.gen.ts'
import { activeLanguage } from '../../i18n/index.ts'
import { createChatEventParser, type ChatEvent } from './sse-parser.ts'
import type { ToolCall } from './tool-status.ts'

type ChatMessageRequest = components['schemas']['ChatMessageRequest']

const MESSAGES_PATH = '/api/chat/conversations/{conversationId}/messages' satisfies keyof paths
const CONVERSATION_PATH = '/api/chat/conversations/{conversationId}' satisfies keyof paths

/** The tickets one tool call returned, as the user reads them in the ticket list (chat ADR 0010). */
export type ToolTickets = components['schemas']['ChatTicketsEvent']

/** An `error` event's code, or `request` when the question never got a complete answer stream. */
export type TurnError = components['schemas']['ChatErrorEvent']['code'] | 'request'

/** A tool call the model began to write and has not finished (chat ADR 0004). */
type ToolStarted = components['schemas']['ChatToolCallStartedEvent']

/**
 * The model's reasoning before the answer: its text so far, when its first
 * piece arrived and, once any other event followed, when that one did.
 */
export interface TurnReasoning {
  text: string
  startedAt: number
  endedAt?: number
}

interface TurnContent {
  id: number
  question: string
  /** The ticket open on the page when the question was asked, which "this ticket" in it means. */
  openTicket?: string
  /** The calls the model made, in the order of their `tool` events, then those it is still writing. */
  tools: Array<ToolCall | ToolStarted>
  tickets: ToolTickets[]
  answer: string
  /** Only a turn whose model returned reasoning has one. */
  reasoning?: TurnReasoning
}

/** A question and its answer so far. Only a turn that ended in an error has an `error`. */
export type Turn = TurnContent &
  ({ status: 'streaming' | 'answered' | 'stopped' } | { status: 'error'; error: TurnError })

/** `call` in place of the step at `index`, or after the last step when there is none. */
const placed = (tools: TurnContent['tools'], index: number, call: ToolCall | ToolStarted) =>
  index < tools.length ? tools.with(index, call) : [...tools, call]

/**
 * Consecutive `reasoning` events form one block, which any other event ends. A
 * turn has one: reasoning that arrives later joins its text (chat ADR 0020).
 */
function endReasoning(turn: Turn, now: number): Turn {
  if (turn.reasoning === undefined || turn.reasoning.endedAt !== undefined) return turn
  return { ...turn, reasoning: { ...turn.reasoning, endedAt: now } }
}

/** The turn after `event`, which arrived at `now`. */
function apply(turn: Turn, event: ChatEvent, now: number): Turn {
  if (turn.status !== 'streaming') return turn
  if (event.type === 'reasoning') {
    const reasoning = turn.reasoning ?? { text: '', startedAt: now }
    return { ...turn, reasoning: { ...reasoning, text: reasoning.text + event.text } }
  }
  const settled = endReasoning(turn, now)
  switch (event.type) {
    case 'toolStarted':
      return { ...settled, tools: placed(settled.tools, event.toolCall, event) }
    case 'tool': {
      // A call's place is the number of calls made before it, where the step of its start waits.
      const made = settled.tools.filter((call) => call.type === 'tool').length
      return { ...settled, tools: placed(settled.tools, made, event) }
    }
    case 'tickets':
      return { ...settled, tickets: [...settled.tickets, event] }
    case 'token':
      return { ...settled, answer: settled.answer + event.text }
    case 'done':
      return { ...settled, status: 'answered' }
    case 'error':
      return { ...settled, status: 'error', error: event.code }
  }
}

/** Posts one question and hands each event of the answer to `onEvent` until the stream ends. */
async function streamAnswer(
  conversationId: string,
  request: ChatMessageRequest,
  signal: AbortSignal,
  onEvent: (event: ChatEvent) => void,
): Promise<void> {
  const response = await fetch(MESSAGES_PATH.replace('{conversationId}', conversationId), {
    method: 'POST',
    headers: requestHeaders('POST', {
      json: true,
      headers: { Accept: 'text/event-stream', 'Accept-Language': activeLanguage() },
    }),
    credentials: 'same-origin',
    body: JSON.stringify(request),
    signal,
  })
  if (!response.ok || !response.body) throw new Error(`Chat answered ${response.status}`)
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  const parse = createChatEventParser()
  for (;;) {
    const { done, value } = await reader.read()
    if (done) return
    for (const event of parse(decoder.decode(value, { stream: true }))) onEvent(event)
  }
}

/**
 * One conversation with the chat: its turns, and the actions on it. Nothing is
 * sent before the first question. The conversation id is a fresh UUID per
 * mount and per "new conversation", so a reload starts over (chat ADR 0005).
 * One answer streams at a time; `send` while it streams does nothing.
 */
export function useChatStream() {
  const [conversationId, setConversationId] = useState(() => crypto.randomUUID())
  const [turns, setTurns] = useState<Turn[]>([])
  const controller = useRef<AbortController | undefined>(undefined)
  const nextId = useRef(0)

  useEffect(() => () => controller.current?.abort(), [])

  const streaming = turns.at(-1)?.status === 'streaming'

  const update = (id: number, change: (turn: Turn) => Turn) =>
    setTurns((current) => current.map((turn) => (turn.id === id ? change(turn) : turn)))

  const ask = (question: string, openTicket: string | undefined, previous: Turn[]) => {
    const id = nextId.current++
    const abort = new AbortController()
    controller.current = abort
    setTurns([
      ...previous,
      { id, question, openTicket, tools: [], tickets: [], answer: '', status: 'streaming' },
    ])
    const onEvent = (event: ChatEvent) => {
      if (abort.signal.aborted) return
      // A monotonic clock, so a clock adjustment mid-turn cannot bend the reasoning's seconds.
      const now = performance.now()
      update(id, (turn) => apply(turn, event, now))
    }
    streamAnswer(conversationId, { text: question, openTicket }, abort.signal, onEvent)
      .catch(() => undefined)
      .finally(() => {
        // A stream that ends without `done` or `error` lost its answer.
        if (!abort.signal.aborted) {
          update(id, (turn) =>
            turn.status === 'streaming' ? { ...turn, status: 'error', error: 'request' } : turn,
          )
        }
      })
  }

  const send = (question: string, openTicket?: string) => {
    if (!streaming) ask(question, openTicket, turns)
  }

  const stop = () => {
    const last = turns.at(-1)
    if (!last || last.status !== 'streaming') return
    controller.current?.abort()
    update(last.id, (turn) => (turn.status === 'streaming' ? { ...turn, status: 'stopped' } : turn))
  }

  /** The failed turn left the server's memory as it was, so the question goes again as it was. */
  const retry = () => {
    const last = turns.at(-1)
    if (last?.status === 'error') ask(last.question, last.openTicket, turns.slice(0, -1))
  }

  const reset = () => {
    controller.current?.abort()
    // Forgetting is best effort: a conversation the server kept is never asked under this id again.
    api<void>(CONVERSATION_PATH.replace('{conversationId}', conversationId), {
      method: 'DELETE',
    }).catch(() => undefined)
    setConversationId(crypto.randomUUID())
    setTurns([])
  }

  return { turns, streaming, send, stop, retry, reset }
}

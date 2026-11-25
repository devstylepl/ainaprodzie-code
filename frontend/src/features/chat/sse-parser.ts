import type { components } from '../../api/types.gen.ts'

export type ChatEvent = components['schemas']['ChatEvent']
export type ChatEventType = ChatEvent['type']

type Fields = Record<string, unknown>

// Exhaustive over the contract's union: an event type the contract gains fails
// to compile here. Each check is the least the panel relies on.
const IS_EVENT: Record<ChatEventType, (data: Fields) => boolean> = {
  reasoning: (data) => typeof data.text === 'string',
  toolStarted: (data) => typeof data.tool === 'string' && typeof data.toolCall === 'number',
  tool: (data) =>
    typeof data.tool === 'string' && typeof data.arguments === 'object' && data.arguments !== null,
  tickets: (data) => typeof data.toolCall === 'number' && Array.isArray(data.tickets),
  token: (data) => typeof data.text === 'string',
  done: () => true,
  error: (data) => typeof data.code === 'string',
}

function toEvent(name: string, data: string): ChatEvent | undefined {
  const isEvent = (IS_EVENT as Record<string, ((data: Fields) => boolean) | undefined>)[name]
  if (!isEvent) return undefined
  let parsed: unknown
  try {
    parsed = JSON.parse(data)
  } catch {
    return undefined
  }
  if (typeof parsed !== 'object' || parsed === null) return undefined
  const fields = parsed as Fields
  return fields.type === name && isEvent(fields) ? (parsed as ChatEvent) : undefined
}

/**
 * A parser for one `text/event-stream` body: feed it the decoded chunks in
 * order and it returns the chat events each chunk completed. A frame may end in
 * a later chunk, a line may end in CR, LF or CRLF, and `data` lines join with a
 * line feed (the SSE format). A frame whose event name is not a chat event
 * type, or whose data is not that event, is skipped.
 */
export function createChatEventParser(): (chunk: string) => ChatEvent[] {
  let buffer = ''
  let name = ''
  let data: string[] = []

  const line = (text: string): ChatEvent | undefined => {
    if (text === '') {
      const event = data.length > 0 ? toEvent(name, data.join('\n')) : undefined
      name = ''
      data = []
      return event
    }
    if (text.startsWith(':')) return undefined
    const colon = text.indexOf(':')
    const field = colon === -1 ? text : text.slice(0, colon)
    let value = colon === -1 ? '' : text.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') name = value
    if (field === 'data') data.push(value)
    return undefined
  }

  return (chunk) => {
    buffer += chunk
    const events: ChatEvent[] = []
    // A trailing CR may be the first half of a CRLF, so it waits for the next chunk.
    const pattern = /\r\n|\r(?!$)|\n/g
    let start = 0
    for (let match = pattern.exec(buffer); match; match = pattern.exec(buffer)) {
      const event = line(buffer.slice(start, match.index))
      if (event) events.push(event)
      start = match.index + match[0].length
    }
    buffer = buffer.slice(start)
    return events
  }
}

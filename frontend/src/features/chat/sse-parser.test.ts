import { describe, expect, it } from 'vitest'
import { createChatEventParser } from './sse-parser.ts'

describe('chat event parser', () => {
  it('reads each event of a stream in order', () => {
    const parse = createChatEventParser()

    const events = parse(
      'event:tool\ndata:{"type":"tool","tool":"searchTickets","arguments":{"statuses":["NEW"]}}\n\n' +
        'event:token\ndata:{"type":"token","text":"RET-1046"}\n\n' +
        'event:done\ndata:{"type":"done"}\n\n',
    )

    expect(events).toEqual([
      { type: 'tool', tool: 'searchTickets', arguments: { statuses: ['NEW'] } },
      { type: 'token', text: 'RET-1046' },
      { type: 'done' },
    ])
  })

  it('reads the reasoning before the answer as events of its own', () => {
    const parse = createChatEventParser()

    const events = parse(
      'event:reasoning\ndata:{"type":"reasoning","text":"The user greets me."}\n\n' +
        'event:token\ndata:{"type":"token","text":"Hello"}\n\n',
    )

    expect(events).toEqual([
      { type: 'reasoning', text: 'The user greets me.' },
      { type: 'token', text: 'Hello' },
    ])
  })

  it('joins a frame split across chunks, also between CR and LF', () => {
    const parse = createChatEventParser()

    expect(parse('event: tok')).toEqual([])
    expect(parse('en\r\ndata: {"type":"token",')).toEqual([])
    expect(parse('"text":"Hello"}\r')).toEqual([])
    expect(parse('\n\r\nevent:done\ndata:{"type":"done"}\n')).toEqual([
      { type: 'token', text: 'Hello' },
    ])
    expect(parse('\n')).toEqual([{ type: 'done' }])
  })

  it('joins multi-line data with a line feed', () => {
    const parse = createChatEventParser()

    const events = parse('event:token\ndata:{"type":"token",\ndata:"text":"a"}\n\n')

    expect(events).toEqual([{ type: 'token', text: 'a' }])
  })

  it('reads the tickets of a tool call, and skips a tickets event without its list', () => {
    const parse = createChatEventParser()
    const tickets = {
      type: 'tickets',
      toolCall: 0,
      tickets: [{ reference: 'RET-1046' }],
      query: null,
    }

    const events = parse(
      'event:tickets\ndata:{"type":"tickets","toolCall":0,"query":null}\n\n' +
        `event:tickets\ndata:${JSON.stringify(tickets)}\n\n`,
    )

    expect(events).toEqual([tickets])
  })

  it('reads a tool call the model started, and skips one without its tool or its number', () => {
    const parse = createChatEventParser()
    const started = { type: 'toolStarted', tool: 'getTicket', toolCall: 0 }

    const events = parse(
      'event:toolStarted\ndata:{"type":"toolStarted","toolCall":0}\n\n' +
        'event:toolStarted\ndata:{"type":"toolStarted","tool":"getTicket"}\n\n' +
        `event:toolStarted\ndata:${JSON.stringify(started)}\n\n`,
    )

    expect(events).toEqual([started])
  })

  it('ignores unknown event names, comments and data that is not an event of its name', () => {
    const parse = createChatEventParser()

    const events = parse(
      ':keep-alive\n\n' +
        'event:thinking\ndata:{"type":"thinking"}\n\n' +
        'data:{"type":"token","text":"no event name"}\n\n' +
        'event:token\ndata:{"type":"done"}\n\n' +
        'event:token\ndata:not json\n\n' +
        'event:error\ndata:{"type":"error","code":"timeout"}\n\n',
    )

    expect(events).toEqual([{ type: 'error', code: 'timeout' }])
  })
})

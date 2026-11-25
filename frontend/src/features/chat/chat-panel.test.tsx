import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useLocation, useParams } from 'react-router'
import { AppShell } from '../../components/ui/app-shell.tsx'
import i18n from '../../i18n/index.ts'
import { jsonResponse, renderRoutes } from '../../test/harness.tsx'
import { SessionGate } from '../auth/session-gate.tsx'
import { ticketDetails, ticketSummary } from '../tickets/ticket-fixtures.ts'
import { useTicket } from '../tickets/use-tickets.ts'

const SUPPORT = {
  id: '00000000-0000-0000-0000-000000000001',
  email: 'support@example.com',
  displayName: 'Sam Support',
  roles: ['SUPPORT'],
  language: 'en',
}
const CUSTOMER = { ...SUPPORT, email: 'user@example.com', roles: ['USER'] }

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

interface ChatCall {
  method: string
  conversationId: string
  headers: Headers
  body: unknown
  signal: AbortSignal | undefined
}

/** An answer stream the test writes into, event by event; aborting the request errors it, as `fetch` does. */
class AnswerStream {
  private controller!: ReadableStreamDefaultController<Uint8Array>
  private readonly encoder = new TextEncoder()
  readonly body = new ReadableStream<Uint8Array>({
    start: (controller) => {
      this.controller = controller
    },
  })

  watch(signal: AbortSignal | undefined) {
    signal?.addEventListener('abort', () =>
      this.controller.error(new DOMException('aborted', 'AbortError')),
    )
  }

  send(data: { type: string } & Record<string, unknown>) {
    this.controller.enqueue(
      this.encoder.encode(`event:${data.type}\ndata:${JSON.stringify(data)}\n\n`),
    )
  }

  finish(data: { type: 'done' } | { type: 'error'; code: string }) {
    this.send(data)
    this.close()
  }

  close() {
    this.controller.close()
  }
}

function stubChat(session: typeof SUPPORT, answers: Array<AnswerStream | Response>) {
  const calls: ChatCall[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const chat = /\/api\/chat\/conversations\/([^/]+)/.exec(url)
      if (!chat) {
        if (url.endsWith('/api/auth/session')) return Promise.resolve(jsonResponse(session))
        if (url.endsWith('/api/tickets/RET-1042'))
          return Promise.resolve(jsonResponse(ticketDetails()))
        return Promise.resolve(jsonResponse({ title: 'unstubbed', status: 404 }, 404))
      }
      calls.push({
        method: init?.method ?? 'GET',
        conversationId: chat[1],
        headers: new Headers(init?.headers),
        body: typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined,
        signal: init?.signal ?? undefined,
      })
      if (init?.method === 'DELETE') return Promise.resolve(new Response(null, { status: 204 }))
      const answer = answers.shift()
      if (!answer) throw new Error('no answer scripted')
      if (answer instanceof Response) return Promise.resolve(answer)
      answer.watch(init?.signal ?? undefined)
      return Promise.resolve(
        new Response(answer.body, { headers: { 'Content-Type': 'text/event-stream' } }),
      )
    }),
  )
  return calls
}

function LocationProbe() {
  const location = useLocation()
  return <p data-testid="location">{location.pathname + location.search}</p>
}

/** Loads the ticket as the ticket page does, so the chat finds it in the query cache. */
function TicketProbe() {
  useTicket(useParams().reference ?? '')
  return <LocationProbe />
}

const ROUTES = [
  {
    path: '/',
    element: (
      <SessionGate>
        <AppShell />
      </SessionGate>
    ),
    children: [
      { index: true, element: <LocationProbe /> },
      { path: '/tickets/:reference', element: <TicketProbe /> },
    ],
  },
]

async function openChat() {
  await userEvent.click(await screen.findByRole('button', { name: 'Chat' }))
  return screen.getByRole('complementary', { name: 'Chat' })
}

const panelState = () => screen.getByRole('complementary', { name: 'Chat' }).dataset.state

/** The steps under the questions, each as its state and its words. */
const steps = (panel: HTMLElement) =>
  [...panel.querySelectorAll<HTMLElement>('[data-step]')].map((step) => [
    step.dataset.step,
    step.textContent,
  ])

describe('ChatPanel', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=csrf-chat'
  })

  afterEach(async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    vi.unstubAllGlobals()
    await i18n.changeLanguage('en')
  })

  it('opens from the header with focus in the input, offering support prompts, and sends nothing', async () => {
    const calls = stubChat(SUPPORT, [])
    renderRoutes(ROUTES, '/')

    const toggle = await screen.findByRole('button', { name: 'Chat' })
    expect(toggle.getAttribute('aria-expanded')).toBe('false')
    const panel = await openChat()

    expect(toggle.getAttribute('aria-expanded')).toBe('true')
    expect(panel.dataset.state).toBe('empty')
    expect(document.activeElement).toBe(
      within(panel).getByRole('textbox', { name: 'Your question' }),
    )
    expect(
      within(panel).getByRole('button', { name: 'Which new tickets have breached the SLA?' }),
    ).toBeTruthy()
    expect(within(panel).getByText('The chat only reads tickets.')).toBeTruthy()
    expect(
      within(panel).getByRole('heading', { level: 3, name: 'Ask about your tickets' }),
    ).toBeTruthy()
    expect(calls).toEqual([])
  })

  it('closes on Escape from inside the panel and returns focus to the toggle', async () => {
    stubChat(SUPPORT, [])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()

    await userEvent.keyboard('{Escape}')

    const toggle = screen.getByRole('button', { name: 'Chat' })
    expect(toggle.getAttribute('aria-expanded')).toBe('false')
    expect(panel.dataset.state).toBe('empty')
    expect(document.activeElement).toBe(toggle)
  })

  it('takes the page out of reach while the panel covers it', async () => {
    stubChat(SUPPORT, [])
    renderRoutes(ROUTES, '/')
    const main = await screen.findByRole('main')
    expect(main.hasAttribute('inert')).toBe(false)

    await openChat()
    expect(main.hasAttribute('inert')).toBe(true)

    await userEvent.keyboard('{Escape}')
    expect(main.hasAttribute('inert')).toBe(false)
  })

  it('keeps focus in the question box after a suggested question is asked', async () => {
    const answer = new AnswerStream()
    stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()

    await userEvent.click(
      within(panel).getByRole('button', { name: 'Which new tickets have breached the SLA?' }),
    )

    expect(document.activeElement).toBe(
      within(panel).getByRole('textbox', { name: 'Your question' }),
    )
  })

  it('offers a customer their own prompts', async () => {
    stubChat(CUSTOMER, [])
    renderRoutes(ROUTES, '/')

    const panel = await openChat()

    expect(
      within(panel).getByRole('button', { name: 'Which of our tickets wait for our answer?' }),
    ).toBeTruthy()
    expect(within(panel).queryByRole('button', { name: 'What is assigned to me?' })).toBeNull()
  })

  it('shows the tool call while the answer streams in, then the answer with ticket links', async () => {
    const answer = new AnswerStream()
    const calls = stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()

    await userEvent.click(
      within(panel).getByRole('button', { name: 'Which new tickets have breached the SLA?' }),
    )

    const log = within(panel).getByRole('log')
    await waitFor(() => expect(panelState()).toBe('streaming'))
    expect(log.getAttribute('aria-live')).toBe('polite')
    expect(log.getAttribute('aria-busy')).toBe('true')
    expect(within(panel).getByRole('button', { name: 'Stop' })).toBeTruthy()
    expect(calls).toHaveLength(1)
    expect(calls[0]).toMatchObject({
      method: 'POST',
      body: { text: 'Which new tickets have breached the SLA?' },
    })
    expect(calls[0].conversationId).toMatch(UUID)
    expect(calls[0].headers.get('X-XSRF-TOKEN')).toBe('csrf-chat')
    expect(calls[0].headers.get('Accept-Language')).toBe('en')

    answer.send({
      type: 'tool',
      tool: 'searchTickets',
      arguments: { statuses: ['NEW'], slaBreached: true },
    })
    expect(await within(log).findByText('status New, SLA breached')).toBeTruthy()
    answer.send({ type: 'token', text: 'Two: RET-1046' })
    answer.send({ type: 'token', text: ' and RET-1037.' })
    answer.finish({ type: 'done' })

    await waitFor(() => expect(panelState()).toBe('answered'))
    // The answer keeps flowing in after the stream's end, and the log stays busy until it is all there.
    await waitFor(() => expect(log.getAttribute('aria-busy')).toBe('false'))
    expect(
      within(log)
        .getAllByRole('link')
        .map((link) => link.getAttribute('href')),
    ).toEqual(['/tickets/RET-1046', '/tickets/RET-1037'])
    expect(within(panel).getByRole('button', { name: 'Send' })).toBeTruthy()
  })

  it('asks in the UI language', async () => {
    const answer = new AnswerStream()
    const calls = stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/')
    await userEvent.click(await screen.findByRole('button', { name: 'Polski' }))
    const toggle = await screen.findByRole('button', { name: 'Czat' })
    await userEvent.click(toggle)
    const panel = screen.getByRole('complementary', { name: 'Czat' })

    await userEvent.click(
      within(panel).getByRole('button', { name: 'Które nowe zgłoszenia przekroczyły SLA?' }),
    )

    await waitFor(() => expect(calls).toHaveLength(1))
    expect(calls[0].headers.get('Accept-Language')).toBe('pl')
  })

  it('shows an error event as its message, and Try again asks the same question again', async () => {
    const failed = new AnswerStream()
    const retried = new AnswerStream()
    const calls = stubChat(SUPPORT, [failed, retried])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()
    await ask(panel, 'What is assigned to me?')

    failed.send({ type: 'token', text: 'You have' })
    failed.finish({ type: 'error', code: 'rate-limited' })

    await waitFor(() => expect(panelState()).toBe('error'))
    expect(within(panel).getByRole('alert').textContent).toBe(
      'Too many questions at once. Wait a moment and try again.',
    )

    await userEvent.click(within(panel).getByRole('button', { name: 'Try again' }))
    retried.finish({ type: 'done' })

    await waitFor(() => expect(panelState()).toBe('answered'))
    expect(calls.map((call) => [call.method, call.conversationId, call.body])).toEqual([
      ['POST', calls[0].conversationId, { text: 'What is assigned to me?' }],
      ['POST', calls[0].conversationId, { text: 'What is assigned to me?' }],
    ])
    expect(within(panel).getAllByText('What is assigned to me?')).toHaveLength(1)
    expect(within(panel).queryByRole('alert')).toBeNull()
  })

  it('says that a question holding personal data was not sent, and offers no Try again', async () => {
    const refused = new AnswerStream()
    stubChat(SUPPORT, [refused])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()
    await ask(panel, 'My card is 4111 1111 1111 1111')

    refused.finish({ type: 'error', code: 'guardrails-blocked' })

    await waitFor(() => expect(panelState()).toBe('error'))
    expect(within(panel).getByRole('alert').textContent).toBe(
      'The chat did not send this question: the conversation holds personal data that must not reach the model, such as a card number. Remove it from the question and ask again, or start a new conversation if an earlier answer holds it.',
    )
    expect(within(panel).queryByRole('button', { name: 'Try again' })).toBeNull()
  })

  it('treats a refused request and a stream cut short as errors', async () => {
    const cut = new AnswerStream()
    stubChat(SUPPORT, [jsonResponse({ status: 500 }, 500), cut])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()

    await ask(panel, 'What is assigned to me?')
    await waitFor(() => expect(panelState()).toBe('error'))
    expect(within(panel).getByRole('alert').textContent).toBe('The question could not be sent.')

    await userEvent.click(within(panel).getByRole('button', { name: 'Try again' }))
    cut.send({ type: 'token', text: 'Half an' })
    cut.close()

    await waitFor(() => expect(within(panel).getByRole('alert')).toBeTruthy())
    expect(panelState()).toBe('error')
  })

  it('stops the answer, keeps what arrived, and sends nothing while an answer streams', async () => {
    const answer = new AnswerStream()
    const calls = stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()
    await ask(panel, 'What is assigned to me?')
    answer.send({ type: 'token', text: 'You have three' })
    await within(panel).findByText('You have three')

    const input = within(panel).getByRole('textbox', { name: 'Your question' })
    await userEvent.type(input, 'And the oldest?{Enter}')
    expect(calls).toHaveLength(1)
    expect((input as HTMLTextAreaElement).value).toBe('And the oldest?')

    await userEvent.click(within(panel).getByRole('button', { name: 'Stop' }))

    expect(calls[0].signal?.aborted).toBe(true)
    await waitFor(() => expect(panelState()).toBe('stopped'))
    expect(within(panel).getByText('You have three')).toBeTruthy()
    expect(within(panel).getByText('Stopped before the answer was complete.')).toBeTruthy()
    expect(within(panel).getByRole('button', { name: 'Send' })).toBeTruthy()
  })

  it('traces the work: the step it is on, the steps it finished, and the one a stop cut short', async () => {
    const answer = new AnswerStream()
    stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()
    await ask(panel, 'What is assigned to me?')
    const steps = () => traceSteps(panel)

    await waitFor(() => expect(steps()).toEqual([['active', 'Preparing the answer…']]))

    answer.send({ type: 'tool', tool: 'searchTickets', arguments: { assignedToMe: true } })
    await waitFor(() =>
      expect(steps()).toEqual([['active', 'Searching tickets', 'assigned to me']]),
    )

    answer.send({ type: 'tool', tool: 'getTicket', arguments: { reference: 'RET-1046' } })
    await waitFor(() =>
      expect(steps()).toEqual([
        ['done', 'Searching tickets', 'assigned to me'],
        ['active', 'Reading ticket RET-1046'],
      ]),
    )

    await userEvent.click(within(panel).getByRole('button', { name: 'Stop' }))
    await waitFor(() =>
      expect(steps()).toEqual([
        ['done', 'Searching tickets', 'assigned to me'],
        ['interrupted', 'Reading ticket RET-1046'],
      ]),
    )
  })

  it('shows a step once the model names the tool, and fills it in when the call is complete', async () => {
    const answer = new AnswerStream()
    stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()
    await ask(panel, 'What is RET-1046 about?')
    const steps = () => traceSteps(panel)

    answer.send({ type: 'toolStarted', tool: 'getTicket', toolCall: 0 })
    await waitFor(() => expect(steps()).toEqual([['active', 'Reading a ticket']]))

    answer.send({ type: 'tool', tool: 'getTicket', arguments: { reference: 'RET-1046' } })
    await waitFor(() => expect(steps()).toEqual([['active', 'Reading ticket RET-1046']]))
  })

  it('fills the started steps in the order the calls are made, and a search keeps its list link', async () => {
    const answer = new AnswerStream()
    stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/tickets/RET-1')
    const panel = await openChat()
    await ask(panel, 'What is assigned to me, and what is RET-1046 about?')
    const steps = () => traceSteps(panel)

    answer.send({ type: 'toolStarted', tool: 'searchTickets', toolCall: 0 })
    answer.send({ type: 'toolStarted', tool: 'getTicket', toolCall: 1 })
    await waitFor(() =>
      expect(steps()).toEqual([
        ['active', 'Searching tickets'],
        ['active', 'Reading a ticket'],
      ]),
    )

    answer.send({ type: 'tool', tool: 'searchTickets', arguments: { assignedToMe: true } })
    await waitFor(() =>
      expect(steps()).toEqual([
        ['done', 'Searching tickets', 'assigned to me'],
        ['active', 'Reading a ticket'],
      ]),
    )

    answer.send({ type: 'tool', tool: 'getTicket', arguments: { reference: 'RET-1046' } })
    answer.send({
      type: 'tickets',
      toolCall: 0,
      tickets: [BREACHED],
      query: listQuery({ assigneeId: SUPPORT.id }),
    })
    await waitFor(() =>
      expect(steps()).toEqual([
        ['done', 'Searching tickets', 'assigned to me'],
        ['active', 'Reading ticket RET-1046'],
      ]),
    )
    const search = within(panel)
      .getAllByRole('listitem')
      .filter((item) => item.dataset.tool === 'searchTickets')
    expect(search).toHaveLength(1)
    expect(
      await within(search[0]).findByRole('link', { name: 'Show these in the list' }),
    ).toBeTruthy()
  })

  it('keeps the spinner on a step the model is writing, and marks it cut short when the turn fails', async () => {
    const answer = new AnswerStream()
    stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()
    await ask(panel, 'What are RET-1046 and RET-1037 about?')
    const steps = () => traceSteps(panel)

    answer.send({ type: 'token', text: 'Let me read them.' })
    answer.send({ type: 'toolStarted', tool: 'getTicket', toolCall: 0 })
    answer.send({ type: 'toolStarted', tool: 'getTicket', toolCall: 1 })
    await waitFor(() =>
      expect(steps()).toEqual([
        ['active', 'Reading a ticket'],
        ['active', 'Reading a ticket'],
      ]),
    )

    answer.finish({ type: 'error', code: 'unavailable' })
    await waitFor(() =>
      expect(steps()).toEqual([
        ['interrupted', 'Reading a ticket'],
        ['interrupted', 'Reading a ticket'],
      ]),
    )
  })

  describe('reasoning', () => {
    it('shows Thinking where the preparing step stood, with the reasoning flowing under it', async () => {
      const answer = new AnswerStream()
      stubChat(SUPPORT, [answer])
      renderRoutes(ROUTES, '/')
      const panel = await openChat()
      await ask(panel, 'What is an SLA?')
      await waitFor(() => expect(steps(panel)).toEqual([['active', 'Preparing the answer…']]))

      answer.send({ type: 'reasoning', text: 'The user asks ' })
      answer.send({ type: 'reasoning', text: 'what an SLA is.' })

      await waitFor(() => expect(steps(panel)).toEqual([['active', 'Thinking']]))
      expect(within(panel).queryByText('Preparing the answer…')).toBeNull()
      const flowing = await within(panel).findByText('The user asks what an SLA is.')
      expect(flowing.closest('[aria-hidden="true"]')).not.toBeNull()
      expect(within(panel).queryByRole('button', { name: 'Show reasoning' })).toBeNull()
      expect(panelState()).toBe('streaming')
    })

    it('folds to the time it thought on the first word of the answer, and Show reasoning unfolds the text', async () => {
      const now = vi.spyOn(performance, 'now')
      try {
        const answer = new AnswerStream()
        stubChat(SUPPORT, [answer])
        renderRoutes(ROUTES, '/')
        const panel = await openChat()
        await ask(panel, 'What is an SLA?')
        now.mockReturnValue(1_000_000)
        answer.send({ type: 'reasoning', text: 'The user asks ' })
        answer.send({ type: 'reasoning', text: 'what an SLA is.' })
        await waitFor(() => expect(steps(panel)).toEqual([['active', 'Thinking']]))

        now.mockReturnValue(1_003_400)
        answer.send({ type: 'token', text: 'An SLA is a promise.' })

        await waitFor(() => expect(steps(panel)).toEqual([['done', 'Thought for 3 s']]))
        const show = within(panel).getByRole('button', { name: 'Show reasoning' })
        expect(show.getAttribute('aria-expanded')).toBe('false')
        expect(within(panel).queryByText('The user asks what an SLA is.')).toBeNull()

        await userEvent.click(show)

        expect(show.getAttribute('aria-expanded')).toBe('true')
        const text = within(panel).getByText('The user asks what an SLA is.')
        expect(text.closest('[aria-hidden="true"]')).toBeNull()
        expect(text.closest('[aria-live="off"]')).not.toBeNull()
        answer.finish({ type: 'done' })
        await waitFor(() => expect(panelState()).toBe('answered'))
        expect(steps(panel)).toEqual([['done', 'Thought for 3 s']])
        expect(await within(panel).findByText('An SLA is a promise.')).toBeTruthy()
      } finally {
        now.mockRestore()
      }
    })

    it('keeps the step in its stopped state above the stop line when stopped while reasoning', async () => {
      const answer = new AnswerStream()
      stubChat(SUPPORT, [answer])
      renderRoutes(ROUTES, '/')
      const panel = await openChat()
      await ask(panel, 'What is an SLA?')
      answer.send({ type: 'reasoning', text: 'The user asks what an SLA is.' })
      await waitFor(() => expect(steps(panel)).toEqual([['active', 'Thinking']]))

      await userEvent.click(within(panel).getByRole('button', { name: 'Stop' }))

      await waitFor(() => expect(panelState()).toBe('stopped'))
      expect(steps(panel)).toEqual([['interrupted', 'Thinking']])
      const step = panel.querySelector('[data-step]')!
      const stopLine = within(panel).getByText('Stopped before the answer was complete.')
      expect(step.compareDocumentPosition(stopLine) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
      await userEvent.click(within(panel).getByRole('button', { name: 'Show reasoning' }))
      expect(within(panel).getByText('The user asks what an SLA is.')).toBeTruthy()
    })

    it('keeps the step of a turn that failed while reasoning, and Try again starts without it', async () => {
      const failed = new AnswerStream()
      const again = new AnswerStream()
      stubChat(SUPPORT, [failed, again])
      renderRoutes(ROUTES, '/')
      const panel = await openChat()
      await ask(panel, 'What is an SLA?')
      failed.send({ type: 'reasoning', text: 'The user asks what an SLA is.' })
      failed.finish({ type: 'error', code: 'failed' })

      await waitFor(() => expect(panelState()).toBe('error'))
      expect(steps(panel)).toEqual([['interrupted', 'Thinking']])
      const step = panel.querySelector('[data-step]')!
      const problem = within(panel).getByRole('alert')
      expect(step.compareDocumentPosition(problem) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()

      await userEvent.click(within(panel).getByRole('button', { name: 'Try again' }))

      await waitFor(() => expect(steps(panel)).toEqual([['active', 'Preparing the answer…']]))
      expect(within(panel).queryByRole('button', { name: 'Show reasoning' })).toBeNull()
    })
  })

  it('starts a new conversation: forgets the old one on the server and asks under a new id', async () => {
    const first = new AnswerStream()
    const second = new AnswerStream()
    const calls = stubChat(SUPPORT, [first, second])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()
    await ask(panel, 'What is assigned to me?')
    first.finish({ type: 'done' })
    await waitFor(() => expect(panelState()).toBe('answered'))

    await userEvent.click(within(panel).getByRole('button', { name: 'New conversation' }))

    expect(panelState()).toBe('empty')
    expect(document.activeElement).toBe(
      within(panel).getByRole('textbox', { name: 'Your question' }),
    )
    await ask(panel, 'Which new tickets have breached the SLA?')
    await waitFor(() => expect(calls).toHaveLength(3))
    const [question, forget, next] = calls
    expect([forget.method, forget.conversationId]).toEqual(['DELETE', question.conversationId])
    expect(forget.headers.get('X-XSRF-TOKEN')).toBe('csrf-chat')
    expect(next.conversationId).toMatch(UUID)
    expect(next.conversationId).not.toBe(question.conversationId)
  })

  it('keeps the conversation while the user follows a ticket link and comes back', async () => {
    const answer = new AnswerStream()
    stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()
    await ask(panel, 'What is assigned to me?')
    answer.send({ type: 'token', text: 'RET-1046 is yours.' })
    answer.finish({ type: 'done' })
    await waitFor(() => expect(panelState()).toBe('answered'))

    await userEvent.click(await within(panel).findByRole('link', { name: 'RET-1046' }))
    expect((await screen.findByTestId('location')).textContent).toBe('/tickets/RET-1046')
    // Narrow screens close the full-width panel to show the ticket behind it.
    expect(screen.getByRole('button', { name: 'Chat' }).getAttribute('aria-expanded')).toBe('false')
    // Focus lands on the page the link opened, not on the body behind a closed panel.
    expect(document.activeElement).toBe(screen.getByRole('main'))

    await openChat()
    expect(within(panel).getByText('What is assigned to me?')).toBeTruthy()
    expect(panelState()).toBe('answered')
  })
})

describe('ChatPanel on a ticket page', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=csrf-chat'
  })

  afterEach(async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    vi.unstubAllGlobals()
    await i18n.changeLanguage('en')
  })

  const box = (panel: HTMLElement) => within(panel).getByRole('textbox', { name: 'Your question' })

  it('names the open ticket above the box, sends it with the question and stamps the question with it', async () => {
    const answer = new AnswerStream()
    const calls = stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/tickets/RET-1042')
    const panel = await openChat()

    const context = await within(panel).findByRole('group', { name: 'Asking about RET-1042' })
    expect(within(context).getByRole('link', { name: 'RET-1042' }).getAttribute('href')).toBe(
      '/tickets/RET-1042',
    )
    expect(within(context).getByText('Export does not work')).toBeTruthy()
    expect(box(panel).getAttribute('placeholder')).toBe('Ask about RET-1042')
    // The announcement is a region of its own: the row's link and button are not inside it.
    const announcement = within(panel).getByText('Asking about RET-1042')
    expect(announcement.getAttribute('aria-live')).toBe('polite')
    expect(context.closest('[aria-live]')).toBeNull()

    await userEvent.type(box(panel), 'What is this ticket about?{Enter}')

    expect(calls[0].body).toEqual({ text: 'What is this ticket about?', openTicket: 'RET-1042' })
    const stamp = within(within(panel).getByRole('log')).getByRole('link', { name: 'on RET-1042' })
    expect(stamp.getAttribute('href')).toBe('/tickets/RET-1042')
    answer.finish({ type: 'done' })
  })

  it('offers questions about the open ticket in an empty conversation', async () => {
    stubChat(CUSTOMER, [])
    renderRoutes(ROUTES, '/tickets/RET-1042')
    const panel = await openChat()

    expect(
      await within(panel).findByRole('button', { name: 'Where does this ticket stand?' }),
    ).toBeTruthy()
    expect(
      within(panel).queryByRole('button', { name: 'Which of our tickets wait for our answer?' }),
    ).toBeNull()
  })

  it('stops sending the ticket once its context is removed, until a ticket is opened again', async () => {
    const answer = new AnswerStream()
    const calls = stubChat(SUPPORT, [answer])
    const router = renderRoutes(ROUTES, '/tickets/RET-1042')
    const panel = await openChat()

    await userEvent.click(
      await within(panel).findByRole('button', { name: 'Stop asking about RET-1042' }),
    )

    expect(within(panel).queryByRole('group', { name: 'Asking about RET-1042' })).toBeNull()
    expect(document.activeElement).toBe(box(panel))
    expect(box(panel).getAttribute('placeholder')).toBe('Ask about tickets')
    expect(
      within(panel).getByRole('button', { name: 'Which new tickets have breached the SLA?' }),
    ).toBeTruthy()
    await userEvent.type(box(panel), 'Which tickets are new?{Enter}')
    expect(calls[0].body).toEqual({ text: 'Which tickets are new?' })
    expect(within(within(panel).getByRole('log')).queryByRole('link')).toBeNull()
    answer.finish({ type: 'done' })

    await act(() => router.navigate('/'))
    await act(() => router.navigate('/tickets/RET-1042'))
    expect(await within(panel).findByRole('group', { name: 'Asking about RET-1042' })).toBeTruthy()
  })

  it('names no ticket on a ticket that did not load', async () => {
    stubChat(SUPPORT, [])
    renderRoutes(ROUTES, '/tickets/RET-9999')
    const panel = await openChat()

    expect(box(panel).getAttribute('placeholder')).toBe('Ask about tickets')
    expect(within(panel).queryByRole('group')).toBeNull()
  })
})

const DEADLINE = '2026-10-08T12:00:00Z'

const BREACHED = ticketSummary({
  reference: 'RET-1046',
  title: 'Gantt export is blank',
  sla: { responseDeadline: DEADLINE, waitingSeconds: (16 * 24 + 1) * 3600, breached: true },
})

const WITHIN_SLA = ticketSummary({
  reference: 'RET-1037',
  title: 'Invoice shows the wrong VAT rate',
  status: 'IN_PROGRESS',
  firstResponseAt: '2026-10-08T11:35:00Z',
  sla: { responseDeadline: DEADLINE, waitingSeconds: 35 * 60, breached: false },
})

/** A search's `GET /api/tickets` query with no criterion, as the tickets event carries it. */
function listQuery(overrides: Record<string, unknown> = {}) {
  return {
    text: null,
    organizationId: null,
    status: [],
    category: [],
    priority: [],
    teamId: null,
    assigneeId: null,
    slaBreached: null,
    authorId: null,
    createdFrom: null,
    createdTo: null,
    deadlineBefore: null,
    sort: 'RECENTLY_UPDATED',
    ...overrides,
  }
}

const answerRows = (panel: HTMLElement) =>
  within(panel).queryByRole('list', { name: 'Tickets in this answer' })

describe('ChatPanel answer rows', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=csrf-chat'
  })

  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('draws the returned tickets the answer names, in the order it names them, once it is complete', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-09T12:00:00Z'))
    const answer = new AnswerStream()
    stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()
    await ask(panel, 'Which new tickets have breached the SLA?')

    answer.send({ type: 'tool', tool: 'searchTickets', arguments: { slaBreached: true } })
    answer.send({
      type: 'tickets',
      toolCall: 0,
      query: null,
      tickets: [BREACHED, WITHIN_SLA, ticketSummary({ reference: 'RET-1050' })],
    })
    answer.send({ type: 'token', text: 'RET-1037 and RET-1046 breached; RET-1099 is older.' })
    await within(panel).findByRole('link', { name: 'RET-1099' })
    expect(answerRows(panel)).toBeNull()
    answer.finish({ type: 'done' })

    const rows = await within(panel).findByRole('list', { name: 'Tickets in this answer' })
    expect(
      within(rows)
        .getAllByRole('listitem')
        .map((row) => row.textContent),
    ).toEqual([
      'RET-1037In progressAnswered in 35 minInvoice shows the wrong VAT rate',
      'RET-1046NewSLASLA breached1 d overGantt export is blank',
    ])
    expect(
      within(rows)
        .getAllByRole('link')
        .map((link) => link.getAttribute('href')),
    ).toEqual(['/tickets/RET-1037', '/tickets/RET-1046'])
    expect(within(rows).getByRole('link', { name: 'RET-1046 Gantt export is blank' })).toBeTruthy()
  })

  it("reads a row's clock as the ticket list's Response column does, for each status", async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-09T12:00:00Z'))
    const waiting = ticketSummary({
      reference: 'RET-1001',
      status: 'WAITING_FOR_CUSTOMER',
      firstResponseAt: '2026-10-08T09:00:00Z',
      sla: { responseDeadline: DEADLINE, waitingSeconds: 3600, breached: false },
    })
    const closed = ticketSummary({
      reference: 'RET-1002',
      status: 'CLOSED',
      closedAt: '2026-10-08T15:00:00Z',
      sla: { responseDeadline: DEADLINE, waitingSeconds: 7 * 3600, breached: true },
    })
    const panel = await answerWith(
      [BREACHED, waiting, closed],
      'RET-1046, RET-1001, RET-1002',
      'done',
    )

    const rows = await within(panel).findByRole('list', { name: 'Tickets in this answer' })
    const [live, answered, closedLate] = within(rows).getAllByRole('listitem')
    const overClock = within(live).getByText('1 d over')
    expect(overClock.dataset.clock).toBe('over')
    expect(within(live).getByText('SLA breached')).toBeTruthy()
    expect(within(answered).getByText('Answered in 1 h').dataset.clock).toBe('answered')
    expect(within(answered).queryByText('SLA breached')).toBeNull()
    const settled = within(closedLate).getByText('Closed unanswered, late')
    expect(settled.closest('[data-clock]')?.getAttribute('data-clock')).toBe('closedLate')
    expect(
      within(closedLate).getByText('Closed without a first response, after the response deadline'),
    ).toBeTruthy()
  })

  it("reads a customer's row clock in the customer's words, with no SLA chip", async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-08T11:00:00Z'))
    const answer = new AnswerStream()
    stubChat(CUSTOMER, [answer])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()
    await ask(panel, 'What is open?')
    answer.send({ type: 'tool', tool: 'searchTickets', arguments: {} })
    answer.send({
      type: 'tickets',
      toolCall: 0,
      query: null,
      tickets: [
        ticketSummary({ reference: 'RET-1001', sla: { ...BREACHED.sla, breached: false } }),
      ],
    })
    answer.send({ type: 'token', text: 'RET-1001 is open.' })
    answer.finish({ type: 'done' })

    const rows = await within(panel).findByRole('list', { name: 'Tickets in this answer' })
    expect(within(rows).getByText('Waiting for support for 3 hours')).toBeTruthy()
    expect(within(rows).queryByText('SLA')).toBeNull()
  })

  it('shows the first five rows, and all of them on request', async () => {
    const tickets = [1, 2, 3, 4, 5, 6, 7].map((n) => ticketSummary({ reference: `RET-100${n}` }))
    const panel = await answerWith(
      tickets,
      tickets.map((ticket) => ticket.reference).join(', '),
      'done',
    )

    const rows = await within(panel).findByRole('list', { name: 'Tickets in this answer' })
    expect(within(rows).getAllByRole('listitem')).toHaveLength(5)
    const more = within(panel).getByRole('button', { name: 'Show all 7' })
    expect(more.getAttribute('aria-expanded')).toBe('false')

    await userEvent.click(more)

    expect(within(rows).getAllByRole('listitem')).toHaveLength(7)
    expect(more.getAttribute('aria-expanded')).toBe('true')
    expect(more.textContent).toBe('Show fewer')
  })

  it("draws a stopped answer's rows for the tickets it named before the stop", async () => {
    const panel = await answerWith([BREACHED, WITHIN_SLA], 'First RET-1046,', 'stop')

    const rows = await within(panel).findByRole('list', { name: 'Tickets in this answer' })
    expect(
      within(rows)
        .getAllByRole('link')
        .map((link) => link.getAttribute('href')),
    ).toEqual(['/tickets/RET-1046'])
  })

  it('draws no rows for an answer that failed', async () => {
    const answer = new AnswerStream()
    stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/')
    const panel = await openChat()
    await ask(panel, 'Which new tickets have breached the SLA?')
    answer.send({ type: 'tool', tool: 'searchTickets', arguments: {} })
    answer.send({ type: 'tickets', toolCall: 0, query: null, tickets: [BREACHED] })
    answer.send({ type: 'token', text: 'RET-1046 is' })
    answer.finish({ type: 'error', code: 'timeout' })

    await waitFor(() => expect(panelState()).toBe('error'))
    expect(answerRows(panel)).toBeNull()
  })

  it("links a search the ticket list can repeat to that list in the search's order, and only such a search", async () => {
    const answer = new AnswerStream()
    stubChat(SUPPORT, [answer])
    renderRoutes(ROUTES, '/tickets/RET-1')
    const panel = await openChat()
    await ask(panel, 'Which of my new tickets breached the SLA, and which were raised this week?')
    const steps = () =>
      within(panel)
        .getAllByRole('listitem')
        .filter((item) => item.dataset.tool !== undefined)

    answer.send({ type: 'tool', tool: 'searchTickets', arguments: { statuses: ['NEW'] } })
    answer.send({ type: 'tool', tool: 'searchTickets', arguments: { createdFrom: DEADLINE } })
    answer.send({
      type: 'tickets',
      toolCall: 0,
      tickets: [BREACHED],
      query: listQuery({ status: ['NEW'], assigneeId: SUPPORT.id, slaBreached: true }),
    })
    answer.send({
      type: 'tickets',
      toolCall: 1,
      tickets: [WITHIN_SLA],
      query: listQuery({ createdFrom: DEADLINE }),
    })

    await waitFor(() => expect(steps()).toHaveLength(2))
    // Support's list opens on the response deadline, so the search's order goes along.
    const address = `/?status=NEW&assigneeId=${SUPPORT.id}&slaBreached=true&sort=RECENTLY_UPDATED`
    const link = await within(steps()[0]).findByRole('link', { name: 'Show these in the list' })
    expect(link.getAttribute('href')).toBe(address)
    expect(within(steps()[1]).queryByRole('link')).toBeNull()

    await userEvent.click(link)
    expect(screen.getByTestId('location').textContent).toBe(address)
  })
})

/** Asks, and answers with one search that returned `tickets` and the answer `text`. */
async function answerWith(
  tickets: ReturnType<typeof ticketSummary>[],
  text: string,
  end: 'done' | 'stop',
) {
  const answer = new AnswerStream()
  stubChat(SUPPORT, [answer])
  renderRoutes(ROUTES, '/')
  const panel = await openChat()
  await ask(panel, 'Which new tickets have breached the SLA?')
  answer.send({ type: 'tool', tool: 'searchTickets', arguments: {} })
  answer.send({ type: 'tickets', toolCall: 0, query: null, tickets })
  answer.send({ type: 'token', text })
  if (end === 'done') {
    answer.finish({ type: 'done' })
  } else {
    await waitFor(() => expect(within(panel).getByRole('log').textContent).toContain(text))
    await userEvent.click(within(panel).getByRole('button', { name: 'Stop' }))
  }
  return panel
}

/** The trace's steps as the reader sees them: each step's state, then its lines. */
function traceSteps(panel: HTMLElement) {
  return within(panel)
    .queryAllByRole('listitem')
    .filter((item) => item.dataset.step !== undefined)
    .map((item) => [
      item.dataset.step,
      ...[...item.querySelectorAll('p')].map((p) => p.textContent),
    ])
}

async function ask(panel: HTMLElement, question: string) {
  const input = within(panel).getByRole('textbox', { name: 'Your question' })
  await userEvent.type(input, question)
  await userEvent.click(within(panel).getByRole('button', { name: 'Send' }))
}

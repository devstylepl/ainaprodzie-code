import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { jsonResponse, renderElement, stubFetch } from '../../test/harness.tsx'
import { SettingsPage, SettingsRoute } from './settings-page.tsx'

const OFF_SENTENCE =
  'Off: only LiteLLM sends traces, one per model call, with the provider model and its cost but without the tool steps between calls.'
const ON_MASKED_SENTENCE =
  "On: the app also sends its own spans, masked by Presidio, and each question shows as one trace with its tool steps and LiteLLM's model calls."
const ON_UNMASKED_SENTENCE =
  'On: the app also sends its own spans, unmasked while "Mask personal data in the app" is off, and each question shows as one trace with its tool steps and LiteLLM\'s model calls.'

const SERVER_ERROR = () =>
  jsonResponse({ type: 'about:blank', title: 'Internal Server Error', status: 500 }, 500)

const TRACES = 'App traces to Phoenix'
const JOIN = 'Group LiteLLM calls per chat message'
const PRESIDIO = 'Mask personal data in the app'

const JOIN_ON_SENTENCE =
  "On: LiteLLM's calls for one chat message share one trace even while app traces are off. The app's spans never arrive then, so Phoenix lists each call as a row of its own within that trace."
const JOIN_OFF_SENTENCE = 'Off: while app traces are off, each LiteLLM call is a trace of its own.'
const JOIN_LOCKED_SENTENCE =
  'App traces already group each question into one trace, so this applies again only while they are off.'

const LLM_SPANS = 'App LLM traces to Phoenix'
const LLM_SPANS_ON_SENTENCE =
  "On: the app's span of a model call is an LLM span with the call's tokens, so a question's trace counts each call's tokens twice, beside LiteLLM's span."
const LLM_SPANS_OFF_SENTENCE =
  "Off: the app's span of a model call is a plain step, so each call's tokens and cost are counted once, on LiteLLM's span."
const LLM_SPANS_LOCKED_SENTENCE =
  'The app sends no spans while app traces are off, so this applies only while they are on.'

const MODEL = 'Model'
const MODELS = ['chat-gemini', 'chat-anthropic']

const FALLBACK = 'Fallback model'
const FALLBACK_OFF_SENTENCE =
  "Off: when the model's provider fails or is too slow, the question is retried, and if that fails too the answer ends with the error."
const FALLBACK_AT_ONCE_SENTENCE =
  "At once: when the model's provider fails or is too slow, LiteLLM sends the question to the fallback model that litellm/config.yaml names for the alias, if any, without a retry."

const REASONING = 'Reasoning'
const REASONING_OFF_SENTENCE = 'Off: the chat does not ask the model to reason before it answers.'
const REASONING_HIGH_SENTENCE =
  'High: the chat asks the model to reason before it answers, with high effort. The answer takes longer and costs more tokens.'

const stored = (
  appTracesExported: boolean,
  gatewayTracesJoined = false,
  presidioInApp = false,
  model = 'chat-gemini',
  appLlmSpansExported = true,
  fallback = 'AT_ONCE',
  reasoning = 'OFF',
) =>
  jsonResponse({
    appTracesExported,
    gatewayTracesJoined,
    appLlmSpansExported,
    presidioInApp,
    model,
    fallback,
    reasoning,
  })

/** Stubs fetch with the configured models after the given routes, which can answer the models first. */
const stub = (routes: Parameters<typeof stubFetch>[0]) =>
  stubFetch([...routes, ['/api/chat/models', () => jsonResponse({ models: MODELS })]])

const PRESIDIO_ON_SENTENCE =
  "On: the app replaces the people it knows with placeholders such as <PERSON_1> before the model reads them, and masks its own spans before they leave. E-mail addresses, phone numbers, IBANs and PESELs are LiteLLM's guardrail's, on or off."
const PRESIDIO_OFF_SENTENCE =
  "Off: names and other personal data reach the model provider as written, and Phoenix shows them in LiteLLM's traces. Only LiteLLM's guardrail masks e-mail addresses, phone numbers, IBANs and PESELs and refuses a card number, and the app's own spans leave unmasked too while app traces are on."

/** Every sentence of a switch is in the page, stacked; only the shown one is exposed to a reader. */
const isShown = (text: string) => screen.getByText(text).getAttribute('aria-hidden') === null

const toggle = (group = TRACES) => within(screen.getByRole('group', { name: group }))
const half = async (name: string, group = TRACES) => {
  await screen.findByRole('group', { name: group })
  return toggle(group).findByRole('button', { name, pressed: true })
}
const other = (name: string, group = TRACES) =>
  toggle(group).getByRole('button', { name, pressed: false })
const button = (name: string, group = TRACES) => toggle(group).getByRole('button', { name })

describe('SettingsPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the stored half pressed with its sentence', async () => {
    stub([['/api/chat/settings', () => stored(false)]])

    renderElement(<SettingsPage />)

    await half('Off')
    expect(other('On')).toBeTruthy()
    expect(isShown(OFF_SENTENCE)).toBe(true)
  })

  it('leads with the personal data group holding the Presidio switch, then the tracing group', async () => {
    stub([['/api/chat/settings', () => stored(false)]])

    renderElement(<SettingsPage />)

    await half('Off', PRESIDIO)
    const groups = screen.getAllByRole('group').filter((it) => !it.hasAttribute('aria-pressed'))
    const named = groups.filter((it) => it.getAttribute('aria-labelledby') !== null)
    expect(named.map((it) => it.textContent?.slice(0, 20))).toEqual([
      expect.stringContaining('Personal data'),
      expect.stringContaining('Tracing in Phoenix'),
    ])
    expect(within(named[0]!).getByRole('group', { name: PRESIDIO })).toBeTruthy()
    expect(within(named[1]!).getByRole('group', { name: TRACES })).toBeTruthy()
    expect(within(named[1]!).getByRole('group', { name: JOIN })).toBeTruthy()
    expect(within(named[0]!).getByText('Presidio')).toBeTruthy()
  })

  it('puts the data-flow window after the switches in reading order', async () => {
    stub([['/api/chat/settings', () => stored(false)]])

    renderElement(<SettingsPage />)

    const window = await screen.findByRole('region', { name: "Where the chat's data goes" })
    const last = screen.getByRole('region', { name: LLM_SPANS })
    expect(last.compareDocumentPosition(window) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('says in the notice who reads names while the Presidio switch is off', async () => {
    stub([['/api/chat/settings', () => stored(false, false, false)]])

    renderElement(<SettingsPage />)

    await half('Off', PRESIDIO)
    expect(isShown(PRESIDIO_OFF_SENTENCE)).toBe(true)
    expect(isShown(PRESIDIO_ON_SENTENCE)).toBe(false)
  })

  it('shows the placeholders sentence while the Presidio switch is on', async () => {
    stub([['/api/chat/settings', () => stored(false, false, true)]])

    renderElement(<SettingsPage />)

    await half('On', PRESIDIO)
    expect(isShown(PRESIDIO_ON_SENTENCE)).toBe(true)
    expect(isShown(PRESIDIO_OFF_SENTENCE)).toBe(false)
  })

  it('presses the other half only once the save succeeded and shows its sentence', async () => {
    let exported = false
    stub([['/api/chat/settings', () => stored(exported)]])
    renderElement(<SettingsPage />)
    await half('Off')
    stub([
      [
        '/api/chat/settings',
        () => {
          exported = true
          return stored(exported)
        },
      ],
    ])

    await userEvent.click(other('On'))

    await half('On')
    expect(isShown(ON_UNMASKED_SENTENCE)).toBe(true)
  })

  it('says the app traces are masked by Presidio only while Presidio in the app is on', async () => {
    stub([['/api/chat/settings', () => stored(true, false, true)]])

    renderElement(<SettingsPage />)

    await half('On')
    expect(isShown(ON_MASKED_SENTENCE)).toBe(true)
    expect(isShown(ON_UNMASKED_SENTENCE)).toBe(false)
  })

  it('says the app traces leave unmasked while Presidio in the app is off', async () => {
    stub([['/api/chat/settings', () => stored(true, false, false)]])

    renderElement(<SettingsPage />)

    await half('On')
    expect(isShown(ON_UNMASKED_SENTENCE)).toBe(true)
    expect(isShown(ON_MASKED_SENTENCE)).toBe(false)
  })

  it('keeps the saved half pressed when a refetch started before the save answers late', async () => {
    stub([['/api/chat/settings', () => stored(false)]])
    renderElement(<SettingsPage />)
    await half('Off')
    let releaseStaleGet = () => {}
    const staleBody = new ReadableStream<Uint8Array>({
      start(controller) {
        releaseStaleGet = () => {
          controller.enqueue(
            new TextEncoder().encode(
              JSON.stringify({
                appTracesExported: false,
                gatewayTracesJoined: false,
                presidioInApp: false,
                appLlmSpansExported: true,
                model: 'chat-gemini',
              }),
            ),
          )
          controller.close()
        }
      },
    })
    const calls = stub([
      [
        '/api/chat/settings',
        () =>
          calls.at(-1)?.method === 'PATCH'
            ? stored(true)
            : new Response(staleBody, { headers: { 'Content-Type': 'application/json' } }),
      ],
    ])
    const visibility = vi.spyOn(document, 'visibilityState', 'get')
    for (const state of ['hidden', 'visible'] as const) {
      visibility.mockReturnValue(state)
      act(() => {
        window.dispatchEvent(new Event('visibilitychange'))
      })
    }
    await waitFor(() => expect(calls.some((call) => call.method === 'GET')).toBe(true))

    await userEvent.click(other('On'))
    await half('On')
    act(() => releaseStaleGet())

    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(button('On').getAttribute('aria-pressed')).toBe('true')
  })

  it('sends only the changed setting in a PATCH', async () => {
    const calls = stub([['/api/chat/settings', () => stored(false)]])
    renderElement(<SettingsPage />)

    await half('Off')
    await userEvent.click(other('On'))

    await waitFor(() => expect(calls.some((call) => call.method === 'PATCH')).toBe(true))
    expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ appTracesExported: true })
  })

  it('sends the joining switch alone and shows its sentence', async () => {
    const calls = stub([
      [
        '/api/chat/settings',
        () => (calls.at(-1)?.method === 'PATCH' ? stored(false, true) : stored(false)),
      ],
    ])
    renderElement(<SettingsPage />)
    await half('Off', JOIN)

    await userEvent.click(other('On', JOIN))

    await half('On', JOIN)
    expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({
      gatewayTracesJoined: true,
    })
    expect(isShown(JOIN_ON_SENTENCE)).toBe(true)
    expect(button('Off').getAttribute('aria-pressed')).toBe('true')
  })

  it('disables the grouping switch with the reason while app traces are on, keeping its stored half pressed', async () => {
    const calls = stub([['/api/chat/settings', () => stored(true, true)]])
    renderElement(<SettingsPage />)

    await half('On', JOIN)

    expect(button('On', JOIN).hasAttribute('disabled')).toBe(true)
    expect(button('Off', JOIN).hasAttribute('disabled')).toBe(true)
    expect(isShown(JOIN_LOCKED_SENTENCE)).toBe(true)
    expect(isShown(JOIN_ON_SENTENCE)).toBe(false)
    await userEvent.click(button('Off', JOIN))
    expect(calls.some((call) => call.method === 'PATCH')).toBe(false)
    expect(button('On', JOIN).getAttribute('aria-pressed')).toBe('true')
  })

  it('enables the grouping switch with its own sentence while app traces are off', async () => {
    stub([['/api/chat/settings', () => stored(false, false)]])
    renderElement(<SettingsPage />)

    await half('Off', JOIN)

    expect(button('On', JOIN).hasAttribute('disabled')).toBe(false)
    expect(button('Off', JOIN).hasAttribute('disabled')).toBe(false)
    expect(isShown(JOIN_LOCKED_SENTENCE)).toBe(false)
    expect(isShown(JOIN_OFF_SENTENCE)).toBe(true)
  })

  it('unlocks the grouping switch once app traces are switched off', async () => {
    let exported = true
    const calls = stub([
      [
        '/api/chat/settings',
        () => {
          if (calls.at(-1)?.method === 'PATCH') exported = false
          return stored(exported)
        },
      ],
    ])
    renderElement(<SettingsPage />)
    await half('On')
    expect(button('Off', JOIN).hasAttribute('disabled')).toBe(true)

    await userEvent.click(button('Off'))

    await half('Off')
    await waitFor(() => expect(button('On', JOIN).hasAttribute('disabled')).toBe(false))
  })

  it('sends the Presidio switch alone', async () => {
    const calls = stub([
      [
        '/api/chat/settings',
        () => (calls.at(-1)?.method === 'PATCH' ? stored(true, false, true) : stored(true)),
      ],
    ])
    renderElement(<SettingsPage />)
    await half('Off', PRESIDIO)

    await userEvent.click(other('On', PRESIDIO))

    await half('On', PRESIDIO)
    expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ presidioInApp: true })
  })

  it('disables the app LLM traces switch with the reason while app traces are off', async () => {
    const calls = stub([['/api/chat/settings', () => stored(false)]])
    renderElement(<SettingsPage />)

    await half('On', LLM_SPANS)

    expect(button('Off', LLM_SPANS).hasAttribute('disabled')).toBe(true)
    expect(isShown(LLM_SPANS_LOCKED_SENTENCE)).toBe(true)
    expect(isShown(LLM_SPANS_ON_SENTENCE)).toBe(false)
    await userEvent.click(button('Off', LLM_SPANS))
    expect(calls.some((call) => call.method === 'PATCH')).toBe(false)
  })

  it('sends the app LLM traces switch alone and shows its sentence', async () => {
    const calls = stub([
      [
        '/api/chat/settings',
        () =>
          calls.at(-1)?.method === 'PATCH'
            ? stored(true, false, false, 'chat-gemini', false)
            : stored(true),
      ],
    ])
    renderElement(<SettingsPage />)
    await half('On', LLM_SPANS)
    expect(isShown(LLM_SPANS_ON_SENTENCE)).toBe(true)

    await userEvent.click(other('Off', LLM_SPANS))

    await half('Off', LLM_SPANS)
    expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({
      appLlmSpansExported: false,
    })
    expect(isShown(LLM_SPANS_OFF_SENTENCE)).toBe(true)
  })

  it('names a region per switch, each holding its own toggle', async () => {
    stub([['/api/chat/settings', () => stored(false)]])

    renderElement(<SettingsPage />)

    await half('Off', PRESIDIO)
    for (const name of [MODEL, PRESIDIO, TRACES, JOIN, LLM_SPANS]) {
      const region = screen.getByRole('region', { name })
      expect(within(region).getByRole('group', { name })).toBeTruthy()
    }
  })

  it('describes each toggle by the sentence it shows', async () => {
    stub([['/api/chat/settings', () => stored(true, true)]])

    renderElement(<SettingsPage />)

    await half('On', JOIN)
    const described = (group: string) =>
      document.getElementById(
        screen.getByRole('group', { name: group }).getAttribute('aria-describedby') ?? '',
      )?.textContent
    expect(described(TRACES)).toBe(ON_UNMASKED_SENTENCE)
    expect(described(JOIN)).toBe(JOIN_LOCKED_SENTENCE)
    expect(described(PRESIDIO)).toBe(PRESIDIO_OFF_SENTENCE)
  })

  it('keeps the old half pressed and shows the problem in the panel of the switch that failed', async () => {
    stub([['/api/chat/settings', () => stored(false)]])
    renderElement(<SettingsPage />)
    await half('Off')
    stub([['/api/chat/settings', SERVER_ERROR]])

    await userEvent.click(button('On'))

    const alert = await within(screen.getByRole('region', { name: TRACES })).findByRole('alert')
    expect(alert.textContent).toBe(
      'Not saved: "App traces to Phoenix" is still off. Something went wrong. Please try again.',
    )
    expect(screen.getAllByRole('alert')).toHaveLength(1)
    expect(button('Off').getAttribute('aria-pressed')).toBe('true')
    expect(button('On').getAttribute('aria-pressed')).toBe('false')
  })

  it('names the switch that failed and its stored state, on or off', async () => {
    stub([['/api/chat/settings', () => stored(false, true)]])
    renderElement(<SettingsPage />)
    await half('On', JOIN)
    stub([['/api/chat/settings', SERVER_ERROR]])

    await userEvent.click(button('Off', JOIN))

    const alert = await within(screen.getByRole('region', { name: JOIN })).findByRole('alert')
    expect(alert.textContent).toBe(
      'Not saved: "Group LiteLLM calls per chat message" is still on. Something went wrong. Please try again.',
    )
    expect(within(screen.getByRole('region', { name: TRACES })).queryByRole('alert')).toBeNull()
    expect(button('On', JOIN).getAttribute('aria-pressed')).toBe('true')
  })

  describe('while saving', () => {
    let release = () => {}
    const slowPatch = (exported: boolean) => {
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          release = () => {
            controller.enqueue(
              new TextEncoder().encode(
                JSON.stringify({
                  appTracesExported: exported,
                  gatewayTracesJoined: false,
                  presidioInApp: false,
                  appLlmSpansExported: true,
                  model: 'chat-gemini',
                }),
              ),
            )
            controller.close()
          }
        },
      })
      return new Response(body, { headers: { 'Content-Type': 'application/json' } })
    }
    const line = (panel: string) =>
      within(screen.getByRole('region', { name: panel })).queryByText(/^(Saving…|Saved)$/)

    it('marks the touched group busy and says Saving in its panel only, ignoring a second press', async () => {
      const calls = stub([
        [
          '/api/chat/settings',
          () => (calls.at(-1)?.method === 'PATCH' ? slowPatch(true) : stored(false)),
        ],
      ])
      renderElement(<SettingsPage />)
      await half('Off')

      await userEvent.click(other('On'))

      await waitFor(() => expect(line(TRACES)?.textContent).toBe('Saving…'))
      expect(line(TRACES)?.dataset.state).toBe('saving')
      expect(screen.getByRole('group', { name: TRACES }).getAttribute('aria-busy')).toBe('true')
      expect(screen.getByRole('group', { name: PRESIDIO }).getAttribute('aria-busy')).not.toBe(
        'true',
      )
      expect(line(PRESIDIO)).toBeNull()
      await userEvent.click(other('On', PRESIDIO))
      expect(calls.filter((call) => call.method === 'PATCH')).toHaveLength(1)
      act(() => release())
      await half('On')
    })

    it('keeps the data-flow window on the stored settings while a save is pending, then follows them', async () => {
      const calls = stub([
        [
          '/api/chat/settings',
          () => (calls.at(-1)?.method === 'PATCH' ? slowPatch(true) : stored(false)),
        ],
      ])
      renderElement(<SettingsPage />)
      await half('Off')
      const appToPhoenix = () =>
        [
          ...document.querySelectorAll(
            'li[data-hop="appToPhoenix"] p, li[data-hop="appToPhoenix"] span',
          ),
        ]
          .filter((it) => !it.closest('[aria-hidden="true"]'))
          .map((it) => it.textContent)
      expect(appToPhoenix()).toEqual(['App → Phoenix', 'Not sent', 'The app sends no spans.'])

      await userEvent.click(other('On'))
      await waitFor(() => expect(line(TRACES)?.textContent).toBe('Saving…'))

      expect(button('On').getAttribute('aria-pressed')).toBe('false')
      expect(appToPhoenix()).toEqual(['App → Phoenix', 'Not sent', 'The app sends no spans.'])
      act(() => release())
      await half('On')
      expect(appToPhoenix()).toEqual([
        'App → Phoenix',
        'As written',
        "The app's spans, personal data included.",
      ])
    })

    it('says Saved in the touched panel once and announces the new sentence in one live region', async () => {
      const calls = stub([
        [
          '/api/chat/settings',
          () => (calls.at(-1)?.method === 'PATCH' ? slowPatch(true) : stored(false)),
        ],
      ])
      renderElement(<SettingsPage />)
      await half('Off')
      expect(screen.getByRole('status').textContent).toBe('')

      await userEvent.click(other('On'))
      act(() => release())

      await waitFor(() => expect(line(TRACES)?.dataset.state).toBe('saved'))
      expect(line(TRACES)?.textContent).toBe('Saved')
      expect(screen.getAllByRole('status')).toHaveLength(1)
      expect(screen.getByRole('status').textContent).toBe(
        `Saved: App traces to Phoenix. ${ON_UNMASKED_SENTENCE}`,
      )
      expect(screen.getByRole('group', { name: TRACES }).getAttribute('aria-busy')).not.toBe('true')
      expect(line(JOIN)).toBeNull()
    })
  })

  describe('fallback model', () => {
    it('shows the stored choice pressed among the three, between the model and the tracing group, with its sentence', async () => {
      stub([['/api/chat/settings', () => stored(false)]])

      renderElement(<SettingsPage />)

      await half('At once', FALLBACK)
      expect(
        toggle(FALLBACK)
          .getAllByRole('button')
          .map((option) => option.textContent),
      ).toEqual(['Off', 'After a retry', 'At once'])
      expect(isShown(FALLBACK_AT_ONCE_SENTENCE)).toBe(true)
      expect(isShown(FALLBACK_OFF_SENTENCE)).toBe(false)
      expect(
        document.getElementById(
          screen.getByRole('group', { name: FALLBACK }).getAttribute('aria-describedby') ?? '',
        )?.textContent,
      ).toBe(FALLBACK_AT_ONCE_SENTENCE)
      const panel = screen.getByRole('region', { name: FALLBACK })
      expect(
        screen.getByRole('region', { name: MODEL }).compareDocumentPosition(panel) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy()
      expect(
        panel.compareDocumentPosition(screen.getByRole('region', { name: TRACES })) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy()
    })

    it('saves the chosen fallback alone and shows its sentence once the save succeeded', async () => {
      const calls = stub([
        [
          '/api/chat/settings',
          () =>
            calls.at(-1)?.method === 'PATCH'
              ? stored(false, false, false, 'chat-gemini', true, 'OFF')
              : stored(false),
        ],
      ])
      renderElement(<SettingsPage />)
      await half('At once', FALLBACK)

      await userEvent.click(other('Off', FALLBACK))

      await half('Off', FALLBACK)
      expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ fallback: 'OFF' })
      expect(isShown(FALLBACK_OFF_SENTENCE)).toBe(true)
      expect(screen.getByRole('status').textContent).toBe(
        `Saved: Fallback model. ${FALLBACK_OFF_SENTENCE}`,
      )
    })

    it('names the choice it is still on when the save fails', async () => {
      stub([['/api/chat/settings', () => stored(false)]])
      renderElement(<SettingsPage />)
      await half('At once', FALLBACK)
      stub([['/api/chat/settings', SERVER_ERROR]])

      await userEvent.click(button('After a retry', FALLBACK))

      const alert = await within(screen.getByRole('region', { name: FALLBACK })).findByRole('alert')
      expect(alert.textContent).toBe(
        'Not saved: "Fallback model" is still set to at once. Something went wrong. Please try again.',
      )
      expect(button('At once', FALLBACK).getAttribute('aria-pressed')).toBe('true')
    })

    it('shows a choice this bundle does not know with no half pressed and no sentence', async () => {
      stub([
        ['/api/chat/settings', () => stored(false, false, false, 'chat-gemini', true, 'NEVER')],
      ])

      renderElement(<SettingsPage />)

      await half('chat-gemini', MODEL)
      expect(
        toggle(FALLBACK)
          .getAllByRole('button')
          .map((option) => option.getAttribute('aria-pressed')),
      ).toEqual(['false', 'false', 'false'])
      expect(isShown(FALLBACK_AT_ONCE_SENTENCE)).toBe(false)
    })
  })

  describe('reasoning', () => {
    const storedReasoning = (reasoning: string) =>
      stored(false, false, false, 'chat-gemini', true, 'AT_ONCE', reasoning)

    it('shows Off pressed among the three, between the fallback model and the tracing group, with its sentence', async () => {
      stub([['/api/chat/settings', () => stored(false)]])

      renderElement(<SettingsPage />)

      await half('Off', REASONING)
      expect(
        toggle(REASONING)
          .getAllByRole('button')
          .map((option) => option.textContent),
      ).toEqual(['Off', 'Medium', 'High'])
      expect(isShown(REASONING_OFF_SENTENCE)).toBe(true)
      expect(isShown(REASONING_HIGH_SENTENCE)).toBe(false)
      expect(
        document.getElementById(
          screen.getByRole('group', { name: REASONING }).getAttribute('aria-describedby') ?? '',
        )?.textContent,
      ).toBe(REASONING_OFF_SENTENCE)
      const panel = screen.getByRole('region', { name: REASONING })
      expect(
        screen.getByRole('region', { name: FALLBACK }).compareDocumentPosition(panel) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy()
      expect(
        panel.compareDocumentPosition(screen.getByRole('region', { name: TRACES })) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy()
    })

    it('saves the chosen effort alone and shows its sentence once the save succeeded', async () => {
      const calls = stub([
        [
          '/api/chat/settings',
          () => (calls.at(-1)?.method === 'PATCH' ? storedReasoning('HIGH') : stored(false)),
        ],
      ])
      renderElement(<SettingsPage />)
      await half('Off', REASONING)

      await userEvent.click(other('High', REASONING))

      await half('High', REASONING)
      expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ reasoning: 'HIGH' })
      expect(isShown(REASONING_HIGH_SENTENCE)).toBe(true)
      expect(screen.getByRole('status').textContent).toBe(
        `Saved: Reasoning. ${REASONING_HIGH_SENTENCE}`,
      )
    })

    it('names the choice it is still on when the save fails', async () => {
      stub([['/api/chat/settings', () => storedReasoning('MEDIUM')]])
      renderElement(<SettingsPage />)
      await half('Medium', REASONING)
      stub([['/api/chat/settings', SERVER_ERROR]])

      await userEvent.click(button('High', REASONING))

      const alert = await within(screen.getByRole('region', { name: REASONING })).findByRole(
        'alert',
      )
      expect(alert.textContent).toBe(
        'Not saved: "Reasoning" is still set to medium. Something went wrong. Please try again.',
      )
      expect(button('Medium', REASONING).getAttribute('aria-pressed')).toBe('true')
    })

    it('shows a choice this bundle does not know with no half pressed and no sentence', async () => {
      stub([['/api/chat/settings', () => storedReasoning('XHIGH')]])

      renderElement(<SettingsPage />)

      await half('chat-gemini', MODEL)
      expect(
        toggle(REASONING)
          .getAllByRole('button')
          .map((option) => option.getAttribute('aria-pressed')),
      ).toEqual(['false', 'false', 'false'])
      expect(isShown(REASONING_OFF_SENTENCE)).toBe(false)
    })
  })

  describe('model', () => {
    it('shows the stored model pressed among the configured aliases, in their order, with its sentence', async () => {
      stub([['/api/chat/settings', () => stored(false, false, false, 'chat-anthropic')]])

      renderElement(<SettingsPage />)

      await half('chat-anthropic', MODEL)
      expect(
        toggle(MODEL)
          .getAllByRole('button')
          .map((option) => option.textContent),
      ).toEqual(MODELS)
      expect(
        document.getElementById(
          screen.getByRole('group', { name: MODEL }).getAttribute('aria-describedby') ?? '',
        )?.textContent,
      ).toBe(
        'Every chat question goes to this LiteLLM alias; litellm/config.yaml maps it to a provider model.',
      )
    })

    it('still offers a stored model that is no longer configured, pressed after the others', async () => {
      stub([['/api/chat/settings', () => stored(false, false, false, 'chat-retired')]])

      renderElement(<SettingsPage />)

      await half('chat-retired', MODEL)
      expect(
        toggle(MODEL)
          .getAllByRole('button')
          .map((option) => option.textContent),
      ).toEqual([...MODELS, 'chat-retired'])
    })

    it('saves the chosen model alone and presses it only once the save succeeded', async () => {
      let release = () => {}
      const calls = stub([
        [
          '/api/chat/settings',
          () => {
            if (calls.at(-1)?.method !== 'PATCH') return stored(true, true)
            const body = new ReadableStream<Uint8Array>({
              start(controller) {
                release = () => {
                  controller.enqueue(
                    new TextEncoder().encode(
                      JSON.stringify({
                        appTracesExported: true,
                        gatewayTracesJoined: true,
                        appLlmSpansExported: true,
                        model: 'chat-anthropic',
                        fallback: 'AT_ONCE',
                      }),
                    ),
                  )
                  controller.close()
                }
              },
            })
            return new Response(body, { headers: { 'Content-Type': 'application/json' } })
          },
        ],
      ])
      renderElement(<SettingsPage />)
      await half('chat-gemini', MODEL)

      await userEvent.click(other('chat-anthropic', MODEL))

      await waitFor(() => expect(calls.some((call) => call.method === 'PATCH')).toBe(true))
      expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({
        model: 'chat-anthropic',
      })
      expect(screen.getByRole('group', { name: MODEL }).getAttribute('aria-busy')).toBe('true')
      expect(button('chat-gemini', MODEL).getAttribute('aria-pressed')).toBe('true')
      expect(button('chat-anthropic', MODEL).getAttribute('aria-pressed')).toBe('false')
      act(() => release())

      await half('chat-anthropic', MODEL)
      await waitFor(() =>
        expect(screen.getByRole('status').textContent).toBe(
          'Saved: Model. Chat questions now go to chat-anthropic.',
        ),
      )
      expect(within(screen.getByRole('region', { name: MODEL })).getByText('Saved')).toBeTruthy()
    })

    it('keeps the stored model pressed and names it when the save fails', async () => {
      stub([['/api/chat/settings', () => stored(false)]])
      renderElement(<SettingsPage />)
      await half('chat-gemini', MODEL)
      stub([['/api/chat/settings', SERVER_ERROR]])

      await userEvent.click(button('chat-anthropic', MODEL))

      const alert = await within(screen.getByRole('region', { name: MODEL })).findByRole('alert')
      expect(alert.textContent).toBe(
        'Not saved: "Model" is still chat-gemini. Something went wrong. Please try again.',
      )
      expect(button('chat-gemini', MODEL).getAttribute('aria-pressed')).toBe('true')
    })

    it('shows the models failure with Try again in its own panel and keeps the switches usable', async () => {
      const calls = stub([
        ['/api/chat/models', SERVER_ERROR],
        [
          '/api/chat/settings',
          () => (calls.at(-1)?.method === 'PATCH' ? stored(true) : stored(false)),
        ],
      ])
      renderElement(<SettingsPage />)

      const region = await screen.findByRole('region', { name: MODEL })
      const alert = await within(region).findByRole('alert')
      expect(alert.textContent).toBe(
        'The models could not be loaded. Something went wrong. Please try again.',
      )
      expect(within(region).getByRole('button', { name: 'Try again' })).toBeTruthy()
      await half('chat-gemini', MODEL)

      await userEvent.click(other('On'))

      await half('On')
      expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({
        appTracesExported: true,
      })
      expect(within(screen.getByRole('region', { name: TRACES })).queryByRole('alert')).toBeNull()
    })
  })

  it('shows the failure with Try again when the settings cannot be loaded', async () => {
    stub([['/api/chat/settings', SERVER_ERROR]])

    renderElement(<SettingsPage />)

    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'On' })).toBeNull()
  })

  it('keeps the loaded view when a refetch fails', async () => {
    stub([['/api/chat/settings', () => stored(true)]])
    renderElement(<SettingsPage />)
    await half('On')
    const calls = stub([['/api/chat/settings', SERVER_ERROR]])

    const visibility = vi.spyOn(document, 'visibilityState', 'get')
    for (const state of ['hidden', 'visible'] as const) {
      visibility.mockReturnValue(state)
      act(() => {
        window.dispatchEvent(new Event('visibilitychange'))
      })
    }

    await waitFor(() => expect(calls.length).toBeGreaterThan(0))
    expect(button('On').getAttribute('aria-pressed')).toBe('true')
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull()
  })
  it('lets the Saved line go quiet after a few seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      const calls = stub([
        [
          '/api/chat/settings',
          () => (calls.at(-1)?.method === 'PATCH' ? stored(true) : stored(false)),
        ],
      ])
      renderElement(<SettingsPage />)
      await half('Off')
      await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(other('On'))
      await screen.findByText('Saved')

      act(() => {
        vi.advanceTimersByTime(5000)
      })

      expect(screen.queryByText('Saved')).toBeNull()
    } finally {
      vi.useRealTimers()
    }
  })
})

describe('SettingsRoute', () => {
  afterEach(() => vi.unstubAllGlobals())

  const session = (roles: string[]) =>
    jsonResponse({
      id: '00000000-0000-0000-0000-000000000001',
      email: 'someone@example.com',
      displayName: 'Someone',
      roles,
      language: 'en',
    })

  it('shows support staff the settings', async () => {
    stub([
      ['/api/auth/session', () => session(['SUPPORT'])],
      ['/api/chat/settings', () => stored(false)],
    ])

    renderElement(<SettingsRoute />)

    expect(await screen.findByRole('region', { name: PRESIDIO })).toBeTruthy()
  })

  it('gives a customer the not-found page without asking for the settings', async () => {
    const calls = stub([
      ['/api/auth/session', () => session(['USER'])],
      ['/api/chat/settings', () => stored(false)],
    ])

    renderElement(<SettingsRoute />)

    await waitFor(() =>
      expect(calls.some((call) => call.url.endsWith('/api/auth/session'))).toBe(true),
    )
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('region', { name: PRESIDIO })).toBeNull()
    expect(screen.queryByRole('heading', { name: 'Settings' })).toBeNull()
    expect(calls.some((call) => call.url.includes('/api/chat/settings'))).toBe(false)
    expect(calls.some((call) => call.url.includes('/api/chat/models'))).toBe(false)
  })
})

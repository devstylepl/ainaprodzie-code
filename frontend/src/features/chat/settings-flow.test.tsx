import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import { renderElement } from '../../test/harness.tsx'
import { DataFlowWindow, type FlowSettings } from './settings-flow.tsx'

const settings = (
  presidioInApp: boolean,
  appTracesExported: boolean,
  gatewayTracesJoined = false,
  fallback: FlowSettings['fallback'] = 'OFF',
): FlowSettings => ({ presidioInApp, appTracesExported, gatewayTracesJoined, fallback })

const row = (hop: string) => document.querySelector<HTMLElement>(`[data-hop="${hop}"]`)!

/** Every value of a fact is in the list, stacked; a reader gets the shown ones only. */
const shown = (hop: string) =>
  [...row(hop).querySelectorAll('span, p')]
    .filter((it) => !it.closest('[aria-hidden="true"]'))
    .map((it) => it.textContent)

const TERMS = {
  appToPresidio: 'App → Presidio',
  appToLitellm: 'App → LiteLLM',
  appToPhoenix: 'App → Phoenix',
  litellmToPresidio: 'LiteLLM → Presidio',
  litellmToProvider: 'LiteLLM → Model provider',
  litellmToPhoenix: 'LiteLLM → Phoenix',
}

const GUARDRAIL = 'e-mail addresses, phone numbers, IBANs and PESELs, and a card number refuses'
const GUARDRAIL_SENTENCE = `The guardrail masks ${GUARDRAIL} the request.`
const PLACEHOLDERS_TO_PROVIDER = `The people as placeholders. ${GUARDRAIL_SENTENCE}`
const NAMES_TO_PROVIDER = `Names as written. The guardrail masks ${GUARDRAIL} the request.`

const SPANS_TO_PRESIDIO = [
  'Sent',
  "The texts of the app's spans, while app traces are on, to mask the personal data in them. Questions and tool results are not sent.",
]

const NOTHING_TO_PRESIDIO = ['Not sent', 'The app asks Presidio nothing.']

/** What the hops that depend on the Presidio switch alone say, switch on. */
const MASKING = {
  appToLitellm: [
    'Placeholders',
    'The people the app knows as placeholders such as <PERSON_1>, everything else as the customers wrote it.',
  ],
  litellmToProvider: ['Placeholders', PLACEHOLDERS_TO_PROVIDER],
  litellmToPhoenix: ['Placeholders', 'Each model call, as the provider received it.'],
}

/** The same hops, switch off: names reach the model provider and Phoenix as written. */
const NOT_MASKING = {
  appToLitellm: [
    'As written',
    'Names and other personal data included, as the customers wrote them.',
  ],
  litellmToProvider: ['Names as written', NAMES_TO_PROVIDER],
  litellmToPhoenix: ['Names as written', 'Each model call, as the provider received it.'],
}

const APP_SPANS = {
  off: ['Not sent', 'The app sends no spans.'],
  masked: ['Masked', "The app's spans, with personal data replaced by its type, such as <PERSON>."],
  asWritten: ['As written', "The app's spans, personal data included."],
}

const GUARDRAIL_HOP = [
  'Sent',
  "Every request, before it goes to the provider: LiteLLM's guardrail.",
]

const SHAPES = {
  withSteps: "One trace per question: the app's tool steps with LiteLLM's model calls.",
  modelCallsOnly: "One trace per question, holding LiteLLM's model calls only.",
  perCall: 'One trace per model call, from LiteLLM only.',
}

describe('DataFlowWindow', () => {
  const COMBINATIONS: Array<
    [string, FlowSettings, typeof MASKING | typeof NOT_MASKING, string[], string, string[]]
  > = [
    [
      'Presidio, traces and grouping all off',
      settings(false, false, false),
      NOT_MASKING,
      APP_SPANS.off,
      SHAPES.perCall,
      NOTHING_TO_PRESIDIO,
    ],
    [
      'grouping only',
      settings(false, false, true),
      NOT_MASKING,
      APP_SPANS.off,
      SHAPES.modelCallsOnly,
      NOTHING_TO_PRESIDIO,
    ],
    [
      'app traces only',
      settings(false, true, false),
      NOT_MASKING,
      APP_SPANS.asWritten,
      SHAPES.withSteps,
      NOTHING_TO_PRESIDIO,
    ],
    [
      'app traces and grouping',
      settings(false, true, true),
      NOT_MASKING,
      APP_SPANS.asWritten,
      SHAPES.withSteps,
      NOTHING_TO_PRESIDIO,
    ],
    [
      'Presidio only',
      settings(true, false, false),
      MASKING,
      APP_SPANS.off,
      SHAPES.perCall,
      NOTHING_TO_PRESIDIO,
    ],
    [
      'Presidio and grouping',
      settings(true, false, true),
      MASKING,
      APP_SPANS.off,
      SHAPES.modelCallsOnly,
      NOTHING_TO_PRESIDIO,
    ],
    [
      'Presidio and app traces',
      settings(true, true, false),
      MASKING,
      APP_SPANS.masked,
      SHAPES.withSteps,
      SPANS_TO_PRESIDIO,
    ],
    [
      'all three on',
      settings(true, true, true),
      MASKING,
      APP_SPANS.masked,
      SHAPES.withSteps,
      SPANS_TO_PRESIDIO,
    ],
  ]

  it.each(COMBINATIONS)(
    'says every hop and how Phoenix groups with %s',
    (_name, stored, personalData, appSpans, shape, spansToPresidio) => {
      renderElement(<DataFlowWindow settings={stored} />)

      expect(shown('appToPresidio')).toEqual([TERMS.appToPresidio, ...spansToPresidio])
      for (const hop of ['appToLitellm', 'litellmToProvider', 'litellmToPhoenix'] as const)
        expect(shown(hop)).toEqual([TERMS[hop], ...personalData[hop]])
      expect(shown('appToPhoenix')).toEqual([TERMS.appToPhoenix, ...appSpans])
      expect(shown('litellmToPresidio')).toEqual([TERMS.litellmToPresidio, ...GUARDRAIL_HOP])
      expect(shown('phoenix')).toEqual(['In Phoenix', shape])
    },
  )

  it.each([
    ['AFTER_RETRY', false, 'Names as written', NAMES_TO_PROVIDER],
    ['AT_ONCE', false, 'Names as written', NAMES_TO_PROVIDER],
    ['AT_ONCE', true, 'Placeholders', PLACEHOLDERS_TO_PROVIDER],
  ] as const)(
    "says the request may reach the fallback model's provider with the fallback %s and Presidio in the app %s",
    (fallback, presidioInApp, stamp, sentence) => {
      renderElement(<DataFlowWindow settings={settings(presidioInApp, false, false, fallback)} />)

      expect(shown('litellmToProvider')).toEqual([
        TERMS.litellmToProvider,
        stamp,
        `${sentence} When the model's provider fails, the same request goes to the fallback model's provider.`,
      ])
    },
  )

  it('draws an edge solid with an arrowhead only where the hop is sent', () => {
    renderElement(<DataFlowWindow settings={settings(false, false)} />)

    const sent = (edge: string) =>
      [...document.querySelectorAll(`[data-edge="${edge}"]`)].map((it) =>
        it.getAttribute('data-sent'),
      )
    expect(sent('appToPresidio')).toEqual(['false'])
    expect(sent('appToPhoenix')).toEqual(['false', 'false', 'false', 'false'])
    for (const edge of [
      'appToLitellm',
      'litellmToPresidio',
      'litellmToProvider',
      'litellmToPhoenix',
    ])
      expect(new Set(sent(edge))).toEqual(new Set(['true']))
  })

  it.each([
    [true, true, 'true'],
    [true, false, 'false'],
    [false, true, 'false'],
    [false, false, 'false'],
  ])(
    'draws the edge to Presidio with the app masking %s and exporting its spans %s as sent: %s',
    (presidioInApp, appTracesExported, sent) => {
      renderElement(<DataFlowWindow settings={settings(presidioInApp, appTracesExported)} />)

      expect(
        [...document.querySelectorAll('[data-edge="appToPresidio"]')].map((it) =>
          it.getAttribute('data-sent'),
        ),
      ).toEqual([sent])
    },
  )

  it('draws every edge sent once Presidio and app traces are on', () => {
    renderElement(<DataFlowWindow settings={settings(true, true)} />)

    const states = [...document.querySelectorAll('[data-edge]')].map((it) =>
      it.getAttribute('data-sent'),
    )
    expect(new Set(states)).toEqual(new Set(['true']))
  })

  it("leaves the stamps and sentences empty and the app's edges undecided while the settings load", () => {
    renderElement(<DataFlowWindow settings={undefined} />)

    for (const hop of [...Object.keys(TERMS), 'phoenix'])
      expect(shown(hop)).toEqual([
        hop === 'phoenix' ? 'In Phoenix' : TERMS[hop as keyof typeof TERMS],
      ])
    const undecided = [...document.querySelectorAll('[data-edge]')]
      .filter((it) => it.getAttribute('data-sent') === null)
      .map((it) => it.getAttribute('data-edge'))
    // LiteLLM's guardrail, the provider and its copy to Phoenix are sent whatever the switches say.
    expect(new Set(undecided)).toEqual(new Set(['appToPresidio', 'appToLitellm', 'appToPhoenix']))
  })

  it('names its section and gives a screen reader the list while the picture is hidden', () => {
    renderElement(<DataFlowWindow settings={settings(true, true)} />)

    const section = screen.getByRole('region', { name: "Where the chat's data goes" })
    expect(within(section).getByRole('heading', { level: 2 }).textContent).toBe(
      "Where the chat's data goes",
    )
    expect(within(section).getAllByRole('listitem')).toHaveLength(6)
    const picture = document.querySelector('[data-edge]')!.closest('[aria-hidden="true"]')!
    expect(picture.textContent).toContain('Runs on this machine')
    expect(within(section).queryByRole('img')).toBeNull()
  })

  it('says how Phoenix groups under the picture, before the hops and outside their list', () => {
    renderElement(<DataFlowWindow settings={settings(false, true)} />)

    const phoenix = row('phoenix')
    const hops = screen.getByRole('list')
    expect(hops.contains(phoenix)).toBe(false)
    const picture = document.querySelector('[data-edge]')!.closest('[aria-hidden="true"]')!
    expect(picture.compareDocumentPosition(phoenix) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(phoenix.compareDocumentPosition(hops) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })
})

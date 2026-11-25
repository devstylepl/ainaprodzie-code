import type { TFunction } from 'i18next'
import type { components } from '../../api/types.gen.ts'
import {
  CATEGORY_KEYS,
  formatDateTime,
  label,
  PRIORITY_KEYS,
  STATUS_KEYS,
} from '../tickets/ticket-labels.ts'

export type ToolCall = components['schemas']['ChatToolCallEvent']

interface Labels {
  /** The `chat` namespace. */
  t: TFunction
  /** The `common` namespace, for the ticket labels. */
  tCommon: TFunction
  language: string
}

type Args = Record<string, unknown>

/** What the chat is doing, and the arguments the model chose for it, as the trace shows them. */
export interface ToolStep {
  action: string
  detail?: string
}

const strings = (value: unknown): string[] =>
  Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []

const text = (value: unknown): string | undefined =>
  typeof value === 'string' && value.trim() !== '' ? value : undefined

function instant(language: string, value: unknown): string | undefined {
  const iso = text(value)
  if (iso === undefined) return undefined
  return Number.isNaN(Date.parse(iso)) ? iso : formatDateTime(language, iso)
}

/**
 * The search's criteria in the order the panel names them. The model writes the
 * arguments, so each one is read for its shape and a value newer than this
 * bundle keeps its raw code. `limit` is left out: it says nothing about what
 * the search looks for.
 */
function criteria(args: Args, { t, tCommon, language }: Labels): string[] {
  const list = (values: string[], keys: Record<string, string>) =>
    new Intl.ListFormat(language, { type: 'disjunction' }).format(
      values.map((value) => label(tCommon, keys, value)),
    )
  const parts: Array<string | undefined> = []
  const named = (key: string, value: string | undefined) =>
    parts.push(value === undefined ? undefined : t(`criteria.${key}`, { value }))
  const enumerated = (key: string, keys: Record<string, string>) => {
    const values = strings(args[key])
    named(key, values.length > 0 ? list(values, keys) : undefined)
  }

  named('text', text(args.text))
  enumerated('statuses', STATUS_KEYS)
  enumerated('categories', CATEGORY_KEYS)
  enumerated('priorities', PRIORITY_KEYS)
  if (typeof args.slaBreached === 'boolean') {
    parts.push(t(args.slaBreached ? 'criteria.slaBreached' : 'criteria.withinSla'))
  }
  if (args.assignedToMe === true) parts.push(t('criteria.assignedToMe'))
  if (args.raisedByMe === true) parts.push(t('criteria.raisedByMe'))
  named('organization', text(args.organization))
  named('team', text(args.team))
  if (args.sort === 'LONGEST_WAITING') parts.push(t('criteria.longestWaiting'))
  named('createdFrom', instant(language, args.createdFrom))
  named('createdTo', instant(language, args.createdTo))
  named('deadlineBefore', instant(language, args.deadlineBefore))
  return parts.filter((part): part is string => part !== undefined)
}

/** One line telling the reader which tool the model called and with what. */
export function toolStatus(call: ToolCall, labels: Labels): ToolStep {
  const { t } = labels
  switch (call.tool) {
    case 'searchTickets': {
      const found = criteria(call.arguments, labels)
      return {
        action: t('tools.searchTickets'),
        detail: found.length > 0 ? found.join(', ') : undefined,
      }
    }
    case 'getTicket': {
      const reference = text(call.arguments.reference)
      return {
        action:
          reference === undefined
            ? t('tools.getTicketUnnamed')
            : t('tools.getTicket', { reference }),
      }
    }
    default:
      return { action: t('tools.unknown', { tool: call.tool }) }
  }
}

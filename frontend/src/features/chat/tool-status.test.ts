import { describe, expect, it } from 'vitest'
import i18n from '../../i18n/index.ts'
import { toolStatus } from './tool-status.ts'

function statusIn(language: 'en' | 'pl', tool: string, args: Record<string, unknown>) {
  return toolStatus(
    { type: 'tool', tool, arguments: args },
    {
      t: i18n.getFixedT(language, 'chat'),
      tCommon: i18n.getFixedT(language, 'common'),
      language,
    },
  )
}

describe('toolStatus', () => {
  it('names each search criterion with the ticket labels', () => {
    expect(
      statusIn('en', 'searchTickets', {
        statuses: ['WAITING_FOR_CUSTOMER'],
        organization: 'Acme Corp',
      }),
    ).toEqual({
      action: 'Searching tickets',
      detail: 'status Waiting for customer, organization Acme Corp',
    })
    expect(
      statusIn('en', 'searchTickets', {
        statuses: ['NEW', 'IN_PROGRESS'],
        priorities: ['HIGH'],
        slaBreached: true,
        assignedToMe: true,
        sort: 'LONGEST_WAITING',
        limit: 5,
      }),
    ).toEqual({
      action: 'Searching tickets',
      detail:
        'status New or In progress, priority High, SLA breached, assigned to me, longest waiting first',
    })
  })

  it('reads in Polish too', () => {
    expect(statusIn('pl', 'searchTickets', { statuses: ['NEW'], slaBreached: true })).toEqual({
      action: 'Szukam zgłoszeń',
      detail: 'status Nowe, SLA przekroczone',
    })
    expect(statusIn('pl', 'getTicket', { reference: 'RET-1015' })).toEqual({
      action: 'Czytam zgłoszenie RET-1015',
    })
  })

  it('shows a search with no criteria, a ticket read and an unknown tool', () => {
    expect(statusIn('en', 'searchTickets', {})).toEqual({ action: 'Searching tickets' })
    expect(statusIn('en', 'getTicket', { reference: 'RET-1015' })).toEqual({
      action: 'Reading ticket RET-1015',
    })
    expect(statusIn('en', 'getTicket', {})).toEqual({ action: 'Reading a ticket' })
    expect(statusIn('en', 'createTicket', { title: 'x' })).toEqual({
      action: 'Using the tool createTicket',
    })
  })

  it('shows values newer than the bundle raw and skips arguments of the wrong shape', () => {
    expect(
      statusIn('en', 'searchTickets', {
        statuses: ['ON_HOLD', 7],
        organization: 42,
        createdFrom: 'yesterday',
      }),
    ).toEqual({ action: 'Searching tickets', detail: 'status ON_HOLD, created from yesterday' })
  })
})

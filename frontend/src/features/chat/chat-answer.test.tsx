import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderElement } from '../../test/harness.tsx'
import { ChatAnswer } from './chat-answer.tsx'

function links() {
  return screen
    .queryAllByRole('link')
    .map((link) => [link.textContent, link.getAttribute('href')] as const)
}

describe('ChatAnswer', () => {
  it('links every ticket reference to the ticket view', async () => {
    renderElement(<ChatAnswer text={'Breached: **RET-1046** and RET-1037.\n\n- RET-1040 waits'} />)

    expect(await screen.findByText('RET-1046')).toBeTruthy()
    expect(links()).toEqual([
      ['RET-1046', '/tickets/RET-1046'],
      ['RET-1037', '/tickets/RET-1037'],
      ['RET-1040', '/tickets/RET-1040'],
    ])
    expect(screen.getByText('RET-1046').closest('strong')).toBeTruthy()
  })

  it('leaves text that only resembles a reference as text', async () => {
    renderElement(<ChatAnswer text="Ask PRJ-8812 or XRET-12 about RET-" />)

    expect(await screen.findByText('Ask PRJ-8812 or XRET-12 about RET-')).toBeTruthy()
    expect(links()).toEqual([])
  })

  it('renders links written by the model as their text, and no images or HTML', async () => {
    const { container } = await renderAnswer(
      'See [the docs](https://evil.example) and [RET-1019](https://evil.example/x).\n\n' +
        '![tracker](https://evil.example/pixel.png)\n\n' +
        '<a href="https://evil.example">raw</a><img src="https://evil.example/raw.png"> <b>bold</b>',
    )

    expect(screen.getByText(/See the docs and/)).toBeTruthy()
    expect(links()).toEqual([['RET-1019', '/tickets/RET-1019']])
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
    expect(container.textContent).not.toContain('evil.example')
  })

  it('renders a table, scrolling inside the answer, with its references linked', async () => {
    await renderAnswer(
      '| Ticket | Status |\n| --- | --- |\n| RET-1019 | Waiting for customer |\n| RET-1030 | New |',
    )

    const table = screen.getByRole('table')
    expect(table.parentElement?.dataset.scroll).toBe('x')
    expect(screen.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
      'Ticket',
      'Status',
    ])
    expect(screen.getAllByRole('row')).toHaveLength(3)
    expect(links()).toEqual([
      ['RET-1019', '/tickets/RET-1019'],
      ['RET-1030', '/tickets/RET-1030'],
    ])
  })

  it('renders bare addresses and footnote references as text, not links', async () => {
    const { container } = await renderAnswer(
      'Docs at https://evil.example/docs or www.evil.example, mail help@evil.example.[^1]\n\n[^1]: RET-1046',
    )

    expect(container.textContent).toContain('https://evil.example/docs')
    expect(container.textContent).toContain('www.evil.example')
    expect(container.textContent).not.toContain('Footnotes')
    expect(container.textContent).not.toContain('↩')
    expect(links()).toEqual([['RET-1046', '/tickets/RET-1046']])
  })
})

async function renderAnswer(text: string) {
  renderElement(
    <div data-testid="answer">
      <ChatAnswer text={text} />
    </div>,
  )
  return { container: await screen.findByTestId('answer') }
}

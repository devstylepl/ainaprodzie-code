import { Box, Link } from '@chakra-ui/react'
import type { Element, ElementContent, Root, RootContent } from 'hast'
import Markdown, { type Components } from 'react-markdown'
import { Link as RouterLink } from 'react-router'
import remarkGfm from 'remark-gfm'
import { REFERENCE_LINK_STYLE } from '../tickets/reference-style.ts'
import { caretBlink } from './chat-motion.ts'
import { REFERENCE } from './ticket-references.ts'

function ticketLink(reference: string): Element {
  return {
    type: 'element',
    tagName: 'a',
    properties: { href: `/tickets/${reference}` },
    children: [{ type: 'text', value: reference }],
  }
}

function linkify(value: string): ElementContent[] {
  const nodes: ElementContent[] = []
  let start = 0
  for (const match of value.matchAll(REFERENCE)) {
    if (match.index > start) nodes.push({ type: 'text', value: value.slice(start, match.index) })
    nodes.push(ticketLink(match[0]))
    start = match.index + match[0].length
  }
  if (start < value.length) nodes.push({ type: 'text', value: value.slice(start) })
  return nodes
}

// GFM's footnote section brings an English "Footnotes" heading (styled by a
// class this app does not define) and "↩" back-references, both links once
// unwrapped would leave as stray text.
const isFootnoteChrome = (node: Element) =>
  node.properties.dataFootnoteBackref !== undefined || node.properties.id === 'footnote-label'

/**
 * The model's links and images go (a link keeps its text), GFM's autolinked
 * addresses included, and every ticket reference left in the text becomes a
 * link to the ticket view. So each link in the answer is one the SPA made, and
 * none points off the app. The ticket view checks visibility, so a link to a
 * ticket the reader may not see is a 404.
 */
function rewrite<T extends RootContent>(nodes: T[]): T[] {
  return nodes.flatMap((node): T[] => {
    if (node.type === 'text') return linkify(node.value) as T[]
    if (node.type !== 'element') return [node]
    if (node.tagName === 'img' || isFootnoteChrome(node)) return []
    const children = rewrite(node.children)
    return node.tagName === 'a' ? (children as T[]) : [{ ...node, children }]
  })
}

function rehypeTicketLinks() {
  return (tree: Root) => {
    tree.children = rewrite(tree.children)
  }
}

const REMARK_PLUGINS = [remarkGfm]
const REHYPE_PLUGINS = [rehypeTicketLinks]

const COMPONENTS: Components = {
  a: ({ href, children }) => (
    // Every link is a ticket reference, so it reads as the queue's reference numbers do.
    <Link asChild {...REFERENCE_LINK_STYLE}>
      <RouterLink to={href ?? '/'}>{children}</RouterLink>
    </Link>
  ),
  // A wide table scrolls inside the answer instead of widening the panel.
  table: ({ children }) => (
    <Box data-scroll="x" overflowX="auto" maxW="full">
      <table>{children}</table>
    </Box>
  ),
}

// While the answer is being written, a caret follows its last word: at the end
// of the last block, or of the last item when the answer ends in a list.
const CARET = {
  content: '""',
  display: 'inline-block',
  w: '2px',
  h: '1em',
  ms: '2px',
  verticalAlign: '-0.15em',
  borderRadius: '1px',
  bg: 'accent.solid',
  ...caretBlink,
}

/**
 * One answer as the model wrote it, in Markdown, re-rendered as tokens arrive.
 * Raw HTML is dropped, never rendered. The model's reasoning goes through it
 * too, in the quieter `reasoning` tone; `compact` leaves no space between
 * blocks, for a window that must hold whole lines.
 */
export function ChatAnswer(props: {
  text: string
  writing?: boolean
  tone?: 'answer' | 'reasoning'
  compact?: boolean
}) {
  const reasoning = props.tone === 'reasoning'
  return (
    <Box
      data-writing={props.writing ? '' : undefined}
      fontSize={reasoning ? '13px' : '14px'}
      lineHeight={reasoning ? '20px' : '1.55'}
      color={reasoning ? 'fg.muted' : undefined}
      overflowWrap="anywhere"
      css={{
        '&[data-writing] > :is(p, h1, h2, h3, h4, blockquote):last-child::after': CARET,
        '&[data-writing] > :is(ul, ol):last-child > li:last-child::after': CARET,
        '& > * + *': { mt: props.compact ? '0' : '8px' },
        '& ul, & ol': { ps: '20px' },
        '& ul': { listStyleType: 'disc' },
        '& ol': { listStyleType: 'decimal' },
        '& li + li': { mt: props.compact ? '0' : '2px' },
        '& code': {
          bg: 'bg.muted',
          borderRadius: 'stamp',
          px: '4px',
          fontFamily: 'mono',
          fontSize: '13px',
        },
        '& h1, & h2, & h3, & h4': { fontWeight: 'bold', fontSize: reasoning ? '13px' : '15px' },
        '& table': { borderCollapse: 'collapse', fontSize: '13px', minW: 'full' },
        '& th, & td': {
          borderWidth: '1px',
          borderColor: 'border',
          px: '8px',
          py: '4px',
          textAlign: 'start',
          verticalAlign: 'top',
          overflowWrap: 'normal',
        },
        // The ticket table's header: mono capitals in Ink 2 on a Card.
        '& th': {
          bg: 'bg.subtle',
          color: 'fg.muted',
          fontFamily: 'mono',
          fontSize: '11px',
          fontWeight: '600',
          letterSpacing: '0.06em',
          textTransform: 'uppercase',
          whiteSpace: 'nowrap',
        },
      }}
    >
      <Markdown
        skipHtml
        remarkPlugins={REMARK_PLUGINS}
        rehypePlugins={REHYPE_PLUGINS}
        components={COMPONENTS}
      >
        {props.text}
      </Markdown>
    </Box>
  )
}

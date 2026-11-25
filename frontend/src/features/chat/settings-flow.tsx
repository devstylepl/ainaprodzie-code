import type { ReactNode } from 'react'
import { Box, Grid, Text } from '@chakra-ui/react'
import { useTranslation } from 'react-i18next'
import { Chip, type ChipTone } from '../../components/ui/chip.tsx'
import { WindowPanel } from '../../components/ui/window-panel.tsx'
import type { ChatSettings } from './use-chat-settings.ts'

/**
 * From this much room on the Settings page the window sits beside the panels instead of after them,
 * and its card's top part sticks. A container query on the page's own width, not a breakpoint: the docked
 * chat narrows the page without changing the window.
 */
export const FLOW_BESIDE = '@container settings-page (min-width: 1020px)'

/** The stored settings the window draws; the model and the app's LLM spans change no hop. */
export type FlowSettings = Pick<
  ChatSettings,
  'appTracesExported' | 'gatewayTracesJoined' | 'presidioInApp' | 'fallback'
>

type NodeId = 'app' | 'presidio' | 'litellm' | 'phoenix' | 'provider'

/** What a hop carries, as the stamp on its edge says it. */
type Form = 'sent' | 'notSent' | 'placeholders' | 'masked' | 'asWritten' | 'namesAsWritten'

interface HopState {
  form: Form
  /** Key of the sentence under `settings.flow.hops.<hop>`. */
  sentence: string
}

const HOPS = {
  appToPresidio: ['app', 'presidio'],
  appToLitellm: ['app', 'litellm'],
  appToPhoenix: ['app', 'phoenix'],
  litellmToPresidio: ['litellm', 'presidio'],
  litellmToProvider: ['litellm', 'provider'],
  litellmToPhoenix: ['litellm', 'phoenix'],
} as const satisfies Record<string, readonly [NodeId, NodeId]>

type HopId = keyof typeof HOPS

const HOP_IDS = Object.keys(HOPS) as HopId[]

/**
 * What each hop carries for the stored switches (the facts are in chat ADR 0009,
 * `litellm/config.yaml` and `SwitchedSpanExporter`): the texts of the app's
 * spans go to Presidio only while the app masks and exports its spans, and never
 * a question or a tool result; LiteLLM's guardrail and its copy to Phoenix
 * are always on; the app's spans leave only while app traces are on, masked
 * only while the app masks; unless the fallback is off, a request whose provider
 * fails goes to the fallback model's provider too (chat ADR 0019), under the
 * sentence keys `placeholdersOrFallback` and `namesAsWrittenOrFallback`.
 */
function hopStates(s: FlowSettings): Record<HopId, HopState> {
  const masks = s.presidioInApp
  const personalData: HopState = masks
    ? { form: 'placeholders', sentence: 'placeholders' }
    : { form: 'namesAsWritten', sentence: 'namesAsWritten' }
  return {
    appToPresidio:
      masks && s.appTracesExported
        ? { form: 'sent', sentence: 'sent' }
        : { form: 'notSent', sentence: 'notSent' },
    appToLitellm: masks
      ? { form: 'placeholders', sentence: 'placeholders' }
      : { form: 'asWritten', sentence: 'asWritten' },
    appToPhoenix: !s.appTracesExported
      ? { form: 'notSent', sentence: 'notSent' }
      : masks
        ? { form: 'masked', sentence: 'masked' }
        : { form: 'asWritten', sentence: 'asWritten' },
    litellmToPresidio: { form: 'sent', sentence: 'sent' },
    litellmToProvider: {
      form: personalData.form,
      sentence: s.fallback === 'OFF' ? personalData.sentence : `${personalData.sentence}OrFallback`,
    },
    litellmToPhoenix: { form: personalData.form, sentence: 'sent' },
  }
}

/** How Phoenix groups what arrives: one trace per question or one per model call. */
function traceShape(s: FlowSettings): string {
  if (s.appTracesExported) return 'perQuestionWithSteps'
  return s.gatewayTracesJoined ? 'perQuestionModelCalls' : 'perModelCall'
}

/** Off or not is all a hop tells of the fallback, so one choice stands for the two that fall back. */
const COMBINATIONS: FlowSettings[] = [false, true].flatMap((appTracesExported) =>
  [false, true].flatMap((gatewayTracesJoined) =>
    [false, true].flatMap((presidioInApp) =>
      (['OFF', 'AT_ONCE'] as const).map((fallback) => ({
        appTracesExported,
        gatewayTracesJoined,
        presidioInApp,
        fallback,
      })),
    ),
  ),
)

/** Every value a fact can take over the combinations, which sizes the slot that holds it. */
const variants = (pick: (s: FlowSettings) => string): string[] => [
  ...new Set(COMBINATIONS.map(pick)),
]

const TONES: Record<Form, ChipTone | undefined> = {
  asWritten: 'ink',
  namesAsWritten: 'ink',
  placeholders: 'neutral',
  masked: 'neutral',
  sent: 'neutral',
  notSent: undefined,
}

/** The state of an edge: the words are the stamp's, the ink and the dashes are the line's. */
function Stamp(props: { form: Form; wrap?: boolean }) {
  const { t } = useTranslation('chat')
  const label = t(`settings.flow.forms.${props.form}`)
  const tone = TONES[props.form]
  if (tone !== undefined)
    return (
      <Chip tone={tone} wrap={props.wrap}>
        {label}
      </Chip>
    )
  return (
    <Text
      as="span"
      display="inline-block"
      bg="bg.panel"
      px="6px"
      fontFamily="mono"
      fontSize="11px"
      lineHeight="16px"
      fontWeight="600"
      letterSpacing="0.06em"
      textTransform="uppercase"
      color="fg.muted"
      whiteSpace={props.wrap ? 'normal' : 'nowrap'}
      textAlign={props.wrap ? 'center' : undefined}
    >
      {label}
    </Text>
  )
}

/** All the values of one fact stacked in one cell, the shown one visible: the cell is as large as the largest. */
function Slot(props: {
  values: string[]
  shown: string | undefined
  render: (value: string) => ReactNode
}) {
  return (
    <Box display="grid">
      {props.values.map((value) => {
        const active = value === props.shown
        return (
          <Box
            key={value}
            gridArea="1 / 1"
            visibility={active ? 'visible' : 'hidden'}
            aria-hidden={active ? undefined : true}
          >
            {props.render(value)}
          </Box>
        )
      })}
    </Box>
  )
}

// The graph is a grid of five columns (the side node, a gap its edge crosses, the spine, a gap, the side node) and
// eight rows, each edge drawn from segments inside the cells it crosses.
const COLUMN = { presidio: 1, gapLeft: 2, spine: 3, gapRight: 4, phoenix: 5 } as const
const ROW = {
  boundary: 1,
  stampsTop: 2,
  app: 3,
  lane: 4,
  litellm: 5,
  stampsBottom: 6,
  below: 7,
  provider: 8,
} as const

/** Whether an edge's words say it is sent; undefined while the settings load. */
type Wire = boolean | undefined

const lineLook = (sent: Wire) =>
  sent === true
    ? { borderStyle: 'solid', borderColor: 'border.control' }
    : sent === false
      ? { borderStyle: 'dashed', borderColor: 'border' }
      : { borderStyle: 'solid', borderColor: 'border' }

/** A segment of an edge inside its cell, from `a` to `b` in percentages along one axis, at `at` across it. */
function Segment(props: {
  edge: HopId
  sent: Wire
  axis: 'x' | 'y'
  at?: number
  a?: number
  b?: number
}) {
  const { at = 50, a = 0, b = 100 } = props
  const look = lineLook(props.sent)
  return props.axis === 'x' ? (
    <Box
      data-edge={props.edge}
      data-sent={props.sent}
      position="absolute"
      left={`${a}%`}
      right={`${100 - b}%`}
      top={`${at}%`}
      borderTopWidth="ink"
      transform="translateY(-50%)"
      {...look}
    />
  ) : (
    <Box
      data-edge={props.edge}
      data-sent={props.sent}
      position="absolute"
      top={`${a}%`}
      bottom={`${100 - b}%`}
      left={`${at}%`}
      borderLeftWidth="ink"
      transform="translateX(-50%)"
      {...look}
    />
  )
}

const HEADS = {
  left: {
    clipPath: 'polygon(0 50%, 100% 0, 100% 100%)',
    left: '0',
    top: '50%',
    transform: 'translateY(-50%)',
  },
  right: {
    clipPath: 'polygon(100% 50%, 0 0, 0 100%)',
    right: '0',
    top: '50%',
    transform: 'translateY(-50%)',
  },
  up: {
    clipPath: 'polygon(50% 0, 0 100%, 100% 100%)',
    top: '0',
    left: '50%',
    transform: 'translateX(-50%)',
  },
  down: {
    clipPath: 'polygon(50% 100%, 0 0, 100% 0)',
    bottom: '0',
    left: '50%',
    transform: 'translateX(-50%)',
  },
} as const

/** The arrowhead of an edge that is sent, its tip on the edge of the cell. */
function Head(props: { dir: keyof typeof HEADS; sent: Wire }) {
  if (props.sent !== true) return null
  return <Box position="absolute" w="9px" h="9px" bg="border.control" {...HEADS[props.dir]} />
}

function Cell(props: { column: number; row: number; span?: number; children?: ReactNode }) {
  return (
    <Box
      position="relative"
      zIndex="1"
      gridColumn={`${props.column} / span ${props.span ?? 1}`}
      gridRow={props.row}
      minW="0"
    >
      {props.children}
    </Box>
  )
}

/** A node: a Sheet box on an ink line whose name never breaks inside a word. */
function Node(props: { column: number; row: number; node: NodeId }) {
  const { t } = useTranslation('chat')
  return (
    <Cell column={props.column} row={props.row}>
      <Box
        h="full"
        minH="36px"
        display="flex"
        alignItems="center"
        justifyContent="center"
        bg="bg.panel"
        borderWidth="ink"
        borderColor="border.control"
        borderRadius="control"
        px="8px"
        py="4px"
        fontFamily="mono"
        fontSize="13px"
        fontWeight="600"
        letterSpacing="0.02em"
        textAlign="center"
        textWrap="balance"
      >
        {t(`settings.flow.nodes.${props.node}`)}
      </Box>
    </Cell>
  )
}

// The stamps of the three edges in the lane between the App and LiteLLM sit at two heights, the middle one higher,
// so two wide ones never touch where the columns are narrow.
const ON_SPINE = 30
const ON_SIDE = 70

/**
 * A stamp laid over its edge, wider than the lane when its words need it: centred on the edge, or with
 * `trailing` hung from the lane's trailing edge, so it never crosses the boundary on the last column.
 */
function OnEdge(props: { form: Form | undefined; at: number; trailing?: boolean }) {
  if (props.form === undefined) return null
  return (
    <Box
      position="absolute"
      top={`${props.at}%`}
      {...(props.trailing
        ? { right: '-6px', transform: 'translateY(-50%)' }
        : { left: '50%', transform: 'translate(-50%, -50%)' })}
    >
      <Stamp form={props.form} />
    </Box>
  )
}

function FlowGraph(props: { states: Record<HopId, HopState> | undefined }) {
  const { t } = useTranslation('chat')
  const sent = (hop: HopId): Wire =>
    props.states === undefined ? undefined : props.states[hop].form !== 'notSent'
  const form = (hop: HopId) => props.states?.[hop].form
  const alwaysSent: Wire = true
  return (
    <Grid
      aria-hidden="true"
      position="relative"
      maxW="520px"
      mx="auto"
      templateColumns="auto minmax(14px, 1fr) auto minmax(14px, 1fr) auto"
      templateRows="auto minmax(28px, auto) auto minmax(72px, auto) auto minmax(44px, auto) minmax(52px, auto) auto"
    >
      <Box
        gridColumn="1 / -1"
        gridRow={`${ROW.boundary} / ${ROW.below}`}
        mx="-10px"
        mb="-10px"
        borderWidth="1px"
        borderStyle="dashed"
        borderColor="border"
        borderRadius="control"
        pointerEvents="none"
      />
      <Text
        gridColumn="1 / -1"
        gridRow={ROW.boundary}
        position="relative"
        zIndex="1"
        pt="8px"
        pb="6px"
        fontFamily="mono"
        fontSize="11px"
        fontWeight="semibold"
        lineHeight="16px"
        letterSpacing="0.06em"
        textTransform="uppercase"
        color="fg.muted"
      >
        {t('settings.flow.boundary')}
      </Text>

      <Node column={COLUMN.presidio} row={ROW.app} node="presidio" />
      <Node column={COLUMN.spine} row={ROW.app} node="app" />
      <Node column={COLUMN.phoenix} row={ROW.litellm} node="phoenix" />
      <Node column={COLUMN.spine} row={ROW.litellm} node="litellm" />
      <Node column={COLUMN.spine} row={ROW.provider} node="provider" />

      {/* App to Presidio, a short way left, its stamp above in the free row. */}
      <Cell column={COLUMN.gapLeft} row={ROW.app}>
        <Segment edge="appToPresidio" sent={sent('appToPresidio')} axis="x" />
        <Head dir="left" sent={sent('appToPresidio')} />
      </Cell>
      <Cell column={COLUMN.gapLeft} row={ROW.stampsTop}>
        <Box position="absolute" bottom="4px" left="50%" transform="translateX(-50%)">
          {form('appToPresidio') && <Stamp form={form('appToPresidio')!} />}
        </Box>
      </Cell>

      {/* App to Phoenix: right, then down the Phoenix column. */}
      <Cell column={COLUMN.gapRight} row={ROW.app}>
        <Segment edge="appToPhoenix" sent={sent('appToPhoenix')} axis="x" />
      </Cell>
      <Cell column={COLUMN.phoenix} row={ROW.app}>
        <Segment edge="appToPhoenix" sent={sent('appToPhoenix')} axis="x" b={50} />
        <Segment edge="appToPhoenix" sent={sent('appToPhoenix')} axis="y" a={50} />
      </Cell>
      <Cell column={COLUMN.phoenix} row={ROW.lane}>
        <Segment edge="appToPhoenix" sent={sent('appToPhoenix')} axis="y" />
        <Head dir="down" sent={sent('appToPhoenix')} />
        <OnEdge form={form('appToPhoenix')} at={ON_SIDE} trailing />
      </Cell>

      {/* App to LiteLLM down the spine. */}
      <Cell column={COLUMN.spine} row={ROW.lane}>
        <Segment edge="appToLitellm" sent={sent('appToLitellm')} axis="y" />
        <Head dir="down" sent={sent('appToLitellm')} />
        <OnEdge form={form('appToLitellm')} at={ON_SPINE} />
      </Cell>

      {/* LiteLLM to Presidio: left, then up the Presidio column. */}
      <Cell column={COLUMN.gapLeft} row={ROW.litellm}>
        <Segment edge="litellmToPresidio" sent={alwaysSent} axis="x" />
      </Cell>
      <Cell column={COLUMN.presidio} row={ROW.litellm}>
        <Segment edge="litellmToPresidio" sent={alwaysSent} axis="x" a={50} />
        <Segment edge="litellmToPresidio" sent={alwaysSent} axis="y" b={50} />
      </Cell>
      <Cell column={COLUMN.presidio} row={ROW.lane}>
        <Segment edge="litellmToPresidio" sent={alwaysSent} axis="y" />
        <Head dir="up" sent={alwaysSent} />
        <OnEdge form={form('litellmToPresidio')} at={ON_SIDE} />
      </Cell>

      {/* LiteLLM to Phoenix, a short way right, its stamp under it. */}
      <Cell column={COLUMN.gapRight} row={ROW.litellm}>
        <Segment edge="litellmToPhoenix" sent={alwaysSent} axis="x" />
        <Head dir="right" sent={alwaysSent} />
      </Cell>
      <Cell column={COLUMN.gapRight} row={ROW.stampsBottom} span={2}>
        <Box pt="6px" display="flex" justifyContent="flex-end">
          <Slot
            values={variants((s) => hopStates(s).litellmToPhoenix.form)}
            shown={form('litellmToPhoenix')}
            render={(value) => (
              <Box display="flex" justifyContent="flex-end">
                <Stamp form={value as Form} wrap />
              </Box>
            )}
          />
        </Box>
      </Cell>

      {/* LiteLLM to the model provider: down the spine, across the boundary. */}
      <Cell column={COLUMN.spine} row={ROW.stampsBottom}>
        <Segment edge="litellmToProvider" sent={alwaysSent} axis="y" />
      </Cell>
      <Cell column={COLUMN.spine} row={ROW.below}>
        <Segment edge="litellmToProvider" sent={alwaysSent} axis="y" />
        <Head dir="down" sent={alwaysSent} />
        <OnEdge form={form('litellmToProvider')} at={50} />
      </Cell>
    </Grid>
  )
}

function HopRow(props: { hop: HopId; states: Record<HopId, HopState> | undefined }) {
  const { t } = useTranslation('chat')
  const [from, to] = HOPS[props.hop]
  const shown = props.states?.[props.hop]
  return (
    <Row
      data-hop={props.hop}
      term={t('settings.flow.hop', {
        from: t(`settings.flow.nodes.${from}`),
        to: t(`settings.flow.nodes.${to}`),
      })}
      stamp={
        <Slot
          values={variants((s) => hopStates(s)[props.hop].form)}
          shown={shown?.form}
          render={(form) => (
            <Box display="flex" justifyContent="flex-end">
              <Stamp form={form as Form} />
            </Box>
          )}
        />
      }
      sentences={variants((s) => hopStates(s)[props.hop].sentence)}
      sentence={shown?.sentence}
      sentenceKey={`settings.flow.hops.${props.hop}`}
    />
  )
}

/** One fact: its name and stamp on a line, its sentence under it, every sentence stacked to hold the height. */
function Row(props: {
  as?: 'li' | 'div'
  term: string
  stamp?: ReactNode
  sentences: string[]
  sentence: string | undefined
  sentenceKey: string
  'data-hop': string
}) {
  const { t } = useTranslation('chat')
  return (
    <Box
      as={props.as ?? 'li'}
      data-hop={props['data-hop']}
      py="10px"
      borderBottomWidth="1px"
      borderBottomStyle="dashed"
      borderColor="border"
      css={{ '&:last-of-type': { borderBottomWidth: '0', paddingBottom: '0' } }}
    >
      <Box
        display="flex"
        flexWrap="wrap"
        alignItems="center"
        columnGap="12px"
        rowGap="4px"
        mb="4px"
      >
        <Text
          flex="1 1 auto"
          minW="0"
          fontFamily="mono"
          fontSize="11px"
          fontWeight="semibold"
          lineHeight="16px"
          letterSpacing="0.06em"
          textTransform="uppercase"
          color="fg.muted"
        >
          {props.term}
        </Text>
        <Box ml="auto">{props.stamp}</Box>
      </Box>
      <Box maxW="60ch" fontSize="14px" lineHeight="20px">
        <Slot
          values={props.sentences}
          shown={props.sentence}
          render={(sentence) => (
            <Text textWrap="pretty">{t(`${props.sentenceKey}.${sentence}`)}</Text>
          )}
        />
      </Box>
    </Box>
  )
}

/**
 * Where the chat's texts go and in what form, for the switches as saved: the
 * graph is a picture of it, the list under it says the same in words and is
 * what a screen reader reads. It follows the stored settings, never a half that
 * is pressed and not yet saved, and reads as empty slots while they load.
 */
export function DataFlowWindow(props: { settings: FlowSettings | undefined }) {
  const { t } = useTranslation('chat')
  const states = props.settings === undefined ? undefined : hopStates(props.settings)
  return (
    // Beside the panels the section runs their full height, so its card's top part, which holds the picture
    // and how Phoenix groups, sticks until the panels end; the hops scroll beneath it. The whole window is
    // taller than the main column at 1366x768.
    <Box
      as="section"
      aria-labelledby="data-flow-heading"
      maxW="640px"
      mb="20px"
      css={{ [FLOW_BESIDE]: { height: '100%', marginBottom: '0' } }}
    >
      <WindowPanel
        name={t('settings.flow.title')}
        nameId="data-flow-heading"
        wrapName
        mb="0"
        borderBottomRadius="0"
        css={{
          [FLOW_BESIDE]: {
            position: 'sticky',
            top: '0',
            zIndex: '1',
            // Paper over the rounded top corners, where the hop rows' side borders would show through.
            boxShadow: '0 -8px 0 4px var(--chakra-colors-bg)',
          },
        }}
        meta={
          <Box as="span" display={{ base: 'none', sm: 'inline' }}>
            {t('settings.flow.meta')}
          </Box>
        }
      >
        <Box px={{ base: '16px', sm: '20px' }} pt="14px" pb="10px">
          <FlowGraph states={states} />
          <Box mt="22px">
            <Row
              as="div"
              data-hop="phoenix"
              term={t('settings.flow.phoenixTerm')}
              sentences={variants(traceShape)}
              sentence={props.settings === undefined ? undefined : traceShape(props.settings)}
              sentenceKey="settings.flow.shapes"
            />
          </Box>
        </Box>
      </WindowPanel>
      {/* The rest of the card, under the top part's ink rule. */}
      <Box
        as="ul"
        listStyleType="none"
        m="0"
        px={{ base: '16px', sm: '20px' }}
        pt="0"
        pb="18px"
        bg="bg.panel"
        borderWidth="ink"
        borderTopWidth="0"
        borderColor="border.control"
        borderBottomRadius="panel"
      >
        {HOP_IDS.map((hop) => (
          <HopRow key={hop} hop={hop} states={states} />
        ))}
      </Box>
    </Box>
  )
}

import { keyframes } from '@emotion/react'

/**
 * The chat's motion (DESIGN.md, the Alive Not Loud Rule): things arrive with a
 * fast, decelerating ease and leave at once. Each style is a set of Chakra
 * props; under `prefers-reduced-motion` the movement goes and a short fade
 * keeps the change of state visible.
 */
const EASE_OUT = 'cubic-bezier(0.16, 1, 0.3, 1)'

const fade = keyframes({ from: { opacity: 0 }, to: { opacity: 1 } })
const rise = keyframes({
  from: { opacity: 0, transform: 'translateY(6px)' },
  to: { opacity: 1, transform: 'none' },
})
const fromEnd = keyframes({
  from: { opacity: 0, transform: 'translateX(24px)' },
  to: { opacity: 1, transform: 'none' },
})
const fromBelow = keyframes({
  from: { opacity: 0, transform: 'translateY(16px)' },
  to: { opacity: 1, transform: 'none' },
})
const settle = keyframes({
  from: { opacity: 0, transform: 'scale(0.4)' },
  to: { opacity: 1, transform: 'none' },
})
// Runs over a path with `pathLength="1"` and `strokeDasharray="1"`.
const draw = keyframes({ from: { strokeDashoffset: 1 }, to: { strokeDashoffset: 0 } })
const blink = keyframes({ '0%, 100%': { opacity: 1 }, '50%': { opacity: 0 } })

const reducedFade = (delay = 0) => ({ animation: `${fade} 150ms linear ${delay}ms both` })

/** An item joining the conversation: a question, a step, an error, an example question. */
export const riseIn = (delay = 0) => ({
  animation: `${rise} 260ms ${EASE_OUT} ${delay}ms both`,
  _motionReduce: reducedFade(delay),
})

/** The answer's first words, which replace the trace's spinner. */
export const fadeIn = { animation: `${fade} 200ms ${EASE_OUT} both` }

/** The panel opening: from the trailing edge beside the page, from below where it covers the page. */
export const panelIn = {
  animation: {
    base: `${fromBelow} 280ms ${EASE_OUT} both`,
    lg: `${fromEnd} 320ms ${EASE_OUT} both`,
  },
  _motionReduce: reducedFade(),
}

/** A step's marker taking its new state. */
export const settleIn = {
  animation: `${settle} 220ms ${EASE_OUT} both`,
  _motionReduce: reducedFade(),
}

/** The check of a finished step, drawn once. */
export const drawIn = {
  strokeDasharray: '1',
  animation: `${draw} 320ms ${EASE_OUT} 60ms both`,
  _motionReduce: { animation: 'none' },
}

/** The caret at the end of an answer that is still being written. */
export const caretBlink = {
  animation: `${blink} 1s steps(1) infinite`,
  _motionReduce: { animation: 'none' },
}

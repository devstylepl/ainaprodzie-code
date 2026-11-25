import { useEffect, useRef, useState } from 'react'

// A backlog shrinks by this fraction per frame, so a big chunk lands in a few
// hundred milliseconds and a trickle of tokens reads at the pace it arrives.
const CATCH_UP = 0.12

const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

/** The end of the word that contains `index`, so a reveal never splits a word or a ticket reference. */
function wordEnd(text: string, index: number): number {
  const space = text.slice(index).search(/\s/)
  return space === -1 ? text.length : index + space
}

/**
 * The part of an answer to show. Tokens arrive in uneven chunks, the last ones
 * often together with the end of the stream; while `flowing`, the text is
 * revealed word by word at a pace that catches up with what has arrived, so
 * the answer flows instead of jumping. `revealing` holds until it has caught
 * up. When the flow is cut (a stop, an error) or motion is reduced, the whole
 * text shows at once.
 */
export function useSmoothText(text: string, flowing: boolean) {
  const smooth = flowing && !reducedMotion()
  const [shown, setShown] = useState(0)
  const shownRef = useRef(0)

  useEffect(() => {
    if (!smooth) return
    let frame = requestAnimationFrame(function step() {
      const backlog = text.length - shownRef.current
      if (backlog <= 0) return
      const next = wordEnd(text, shownRef.current + Math.max(1, Math.ceil(backlog * CATCH_UP)))
      shownRef.current = next
      setShown(next)
      frame = requestAnimationFrame(step)
    })
    return () => cancelAnimationFrame(frame)
  }, [text, smooth])

  return smooth
    ? { text: text.slice(0, shown), revealing: shown < text.length }
    : { text, revealing: false }
}

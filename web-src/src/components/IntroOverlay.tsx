import { useCallback, useEffect, useRef } from 'react'
import { gsap } from 'gsap'
import TextType, { type TextTypeHandle } from './TextType'
import './IntroOverlay.css'

type IntroOverlayProps = {
  onFadeStart: () => void
  onComplete: () => void
}

function IntroOverlay({ onFadeStart, onComplete }: IntroOverlayProps) {
  const rootRef = useRef<HTMLDivElement | null>(null)
  const textRef = useRef<HTMLDivElement | null>(null)
  const textTypeRef = useRef<TextTypeHandle | null>(null)
  const timelineRef = useRef<gsap.core.Timeline | null>(null)
  const finishedRef = useRef(false)
  const fadeStartedRef = useRef(false)
  const skippingRef = useRef(false)

  const finish = useCallback(() => {
    if (finishedRef.current) return
    finishedRef.current = true
    onComplete()
  }, [onComplete])

  const startFade = useCallback(
    (pauseBeforeFade: number, fadeDuration: number) => {
      const root = rootRef.current
      const textEl = textRef.current
      if (!root) {
        onFadeStart()
        finish()
        return
      }

      timelineRef.current?.kill()

      const tl = gsap.timeline({ onComplete: finish })
      timelineRef.current = tl

      tl.to({}, { duration: pauseBeforeFade }).add(() => {
        if (!fadeStartedRef.current) {
          fadeStartedRef.current = true
          onFadeStart()
        }
      })

      if (textEl) {
        tl.to(
          textEl,
          { opacity: 0, duration: fadeDuration, ease: 'power2.inOut' },
          '<',
        )
      }

      tl.to(
        root,
        { opacity: 0, duration: fadeDuration, ease: 'power2.inOut' },
        '<',
      )
    },
    [finish, onFadeStart],
  )

  const handleTypingComplete = useCallback(() => {
    if (skippingRef.current || finishedRef.current) return
    startFade(1.2, 0.85)
  }, [startFade])

  const skipIntro = useCallback(() => {
    if (finishedRef.current || skippingRef.current) return
    skippingRef.current = true

    timelineRef.current?.kill()
    timelineRef.current = null

    textTypeRef.current?.skip()

    if (!fadeStartedRef.current) {
      fadeStartedRef.current = true
      onFadeStart()
    }

    const root = rootRef.current
    const textEl = textRef.current
    if (!root) {
      finish()
      return
    }

    const tl = gsap.timeline({ onComplete: finish })
    timelineRef.current = tl
    if (textEl) {
      tl.to(textEl, { opacity: 0, duration: 0.3, ease: 'power2.inOut' }, 0)
    }
    tl.to(root, { opacity: 0, duration: 0.3, ease: 'power2.inOut' }, 0)
  }, [finish, onFadeStart])

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.code !== 'Space' && event.key !== ' ') return
      event.preventDefault()
      skipIntro()
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [skipIntro])

  return (
    <div
      ref={rootRef}
      className="intro-overlay"
      role="presentation"
      onClick={skipIntro}
    >
      <div ref={textRef} className="intro-overlay__text">
        <TextType
          ref={textTypeRef}
          text={'ти частина спільноти\n— це і є Могилянка'}
          typingSpeed={110}
          variableSpeed={{ min: 90, max: 140 }}
          initialDelay={400}
          onTypingComplete={handleTypingComplete}
        />
      </div>
    </div>
  )
}

export default IntroOverlay

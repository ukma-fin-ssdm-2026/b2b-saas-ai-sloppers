import { useCallback, useRef } from 'react'
import { gsap } from 'gsap'
import TextType from './TextType'
import './IntroOverlay.css'

type IntroOverlayProps = {
  onFadeStart: () => void
  onComplete: () => void
}

function IntroOverlay({ onFadeStart, onComplete }: IntroOverlayProps) {
  const rootRef = useRef<HTMLDivElement | null>(null)
  const textRef = useRef<HTMLDivElement | null>(null)
  const finishedRef = useRef(false)
  const fadeStartedRef = useRef(false)

  const finish = useCallback(() => {
    if (finishedRef.current) return
    finishedRef.current = true
    onComplete()
  }, [onComplete])

  const handleTypingComplete = useCallback(() => {
    const root = rootRef.current
    const textEl = textRef.current
    if (!root) {
      onFadeStart()
      finish()
      return
    }

    const tl = gsap.timeline({ onComplete: finish })

    tl.to({}, { duration: 1.2 }).add(() => {
      if (!fadeStartedRef.current) {
        fadeStartedRef.current = true
        onFadeStart()
      }
    })

    if (textEl) {
      tl.to(textEl, { opacity: 0, duration: 0.85, ease: 'power2.inOut' }, '<')
    }

    tl.to(root, { opacity: 0, duration: 0.85, ease: 'power2.inOut' }, '<')
  }, [finish, onFadeStart])

  return (
    <div ref={rootRef} className="intro-overlay">
      <div ref={textRef} className="intro-overlay__text">
        <TextType
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

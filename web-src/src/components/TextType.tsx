import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from 'react'
import { gsap } from 'gsap'
import './TextType.css'

export type TextTypeHandle = {
  skip: () => void
}

type TextTypeProps = {
  text: string
  typingSpeed?: number
  initialDelay?: number
  showCursor?: boolean
  cursorCharacter?: string
  cursorBlinkDuration?: number
  variableSpeed?: { min: number; max: number }
  onTypingComplete?: () => void
}

const TextType = forwardRef<TextTypeHandle, TextTypeProps>(function TextType(
  {
    text,
    typingSpeed = 110,
    initialDelay = 0,
    showCursor = true,
    cursorCharacter = '_',
    cursorBlinkDuration = 0.5,
    variableSpeed,
    onTypingComplete,
  },
  ref,
) {
  const [displayedText, setDisplayedText] = useState('')
  const [charIndex, setCharIndex] = useState(0)
  const cursorRef = useRef<HTMLSpanElement | null>(null)
  const completedRef = useRef(false)
  const onTypingCompleteRef = useRef(onTypingComplete)
  onTypingCompleteRef.current = onTypingComplete

  const delayForNextChar = useCallback(() => {
    if (!variableSpeed) return typingSpeed
    const { min, max } = variableSpeed
    return Math.random() * (max - min) + min
  }, [variableSpeed, typingSpeed])

  useImperativeHandle(ref, () => ({
    skip: () => {
      if (completedRef.current) return
      completedRef.current = true
      setDisplayedText(text)
      setCharIndex(text.length)
    },
  }))

  useEffect(() => {
    if (!showCursor || !cursorRef.current) return

    gsap.set(cursorRef.current, { opacity: 1 })
    const tween = gsap.to(cursorRef.current, {
      opacity: 0,
      duration: cursorBlinkDuration,
      repeat: -1,
      yoyo: true,
      ease: 'power2.inOut',
    })

    return () => {
      tween.kill()
    }
  }, [showCursor, cursorBlinkDuration])

  useEffect(() => {
    if (charIndex >= text.length) {
      if (!completedRef.current) {
        completedRef.current = true
        onTypingCompleteRef.current?.()
      }
      return
    }

    if (completedRef.current) return

    const timeout = setTimeout(
      () => {
        setDisplayedText((prev) => prev + text.charAt(charIndex))
        setCharIndex((prev) => prev + 1)
      },
      charIndex === 0 ? initialDelay : delayForNextChar(),
    )

    return () => clearTimeout(timeout)
  }, [charIndex, text, initialDelay, delayForNextChar])

  return (
    <div className="text-type">
      <span className="text-type__content">{displayedText}</span>
      {showCursor ? (
        <span ref={cursorRef} className="text-type__cursor">
          {cursorCharacter}
        </span>
      ) : null}
    </div>
  )
})

export default TextType

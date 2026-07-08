import { useEffect, useRef, useState } from 'react'
import { useLoaderStore } from '@/infrastructure/store/loader/loader.store'

export function TopLoader() {
  const activeRequests = useLoaderStore((s) => s.activeRequests)
  const [width, setWidth] = useState(0)
  const [visible, setVisible] = useState(false)
  const [fading, setFading] = useState(false)
  const slowTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const fadeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    const clearTimers = () => {
      if (slowTimerRef.current) clearInterval(slowTimerRef.current)
      if (fadeTimerRef.current) clearTimeout(fadeTimerRef.current)
    }

    if (activeRequests > 0) {
      clearTimers()
      setFading(false)
      setVisible(true)
      setWidth(15)

      const rampTimer = setTimeout(() => setWidth(70), 0)

      slowTimerRef.current = setInterval(() => {
        setWidth((w) => {
          if (w >= 85) return w
          return w + 0.5
        })
      }, 300)

      return () => {
        clearTimeout(rampTimer)
        clearTimers()
      }
    } else {
      clearTimers()
      setWidth(100)
      fadeTimerRef.current = setTimeout(() => {
        setFading(true)
        fadeTimerRef.current = setTimeout(() => {
          setVisible(false)
          setWidth(0)
          setFading(false)
        }, 200)
      }, 150)

      return () => clearTimers()
    }
  }, [activeRequests])

  if (!visible) return null

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        height: '3px',
        width: `${width}%`,
        background: '#f97316',
        boxShadow: '0 0 8px #f97316, 0 0 4px #f97316',
        zIndex: 9999,
        transition: fading
          ? 'opacity 200ms ease-out'
          : 'width 400ms ease-out',
        opacity: fading ? 0 : 1,
        pointerEvents: 'none',
      }}
    />
  )
}

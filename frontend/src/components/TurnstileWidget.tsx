import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'

interface TurnstileRenderOptions {
  sitekey: string
  callback: (token: string) => void
  'expired-callback': () => void
  'error-callback': () => void
}

interface TurnstileGlobal {
  render: (container: HTMLElement, options: TurnstileRenderOptions) => string
  reset: (widgetId: string) => void
  remove: (widgetId: string) => void
}

declare global {
  interface Window {
    turnstile?: TurnstileGlobal
  }
}

const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'

// Shared across every TurnstileWidget instance on the page — Cloudflare's own docs warn against
// fetching api.js more than once (it self-registers as `window.turnstile` on load).
let scriptLoadPromise: Promise<void> | null = null

function loadTurnstileScript(): Promise<void> {
  if (window.turnstile) return Promise.resolve()
  if (!scriptLoadPromise) {
    scriptLoadPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = SCRIPT_SRC
      script.async = true
      script.defer = true
      script.onload = () => resolve()
      script.onerror = () => reject(new Error('Failed to load Turnstile script'))
      document.head.appendChild(script)
    })
  }
  return scriptLoadPromise
}

export interface TurnstileWidgetHandle {
  /** Clears the current (expired or server-rejected) token and re-runs the challenge for a fresh one. */
  reset: () => void
}

interface Props {
  /** Called with the token once the challenge is solved, or null when it expires/errors. */
  onToken: (token: string | null) => void
}

/**
 * Explicit-rendering Cloudflare Turnstile widget. Explicit (not implicit) rendering is required
 * because every form that embeds this is a React-managed SPA form, not a static HTML <form> Turnstile's
 * script can scan on page load — see design.md D4 in the add-turnstile-bot-protection change.
 */
export const TurnstileWidget = forwardRef<TurnstileWidgetHandle, Props>(function TurnstileWidget({ onToken }, ref) {
  const containerRef = useRef<HTMLDivElement>(null)
  const widgetIdRef = useRef<string | null>(null)

  useImperativeHandle(ref, () => ({
    reset: () => {
      onToken(null)
      if (widgetIdRef.current && window.turnstile) {
        window.turnstile.reset(widgetIdRef.current)
      }
    }
  }))

  useEffect(() => {
    let cancelled = false

    loadTurnstileScript()
      .then(() => {
        if (cancelled || !containerRef.current || !window.turnstile) return
        widgetIdRef.current = window.turnstile.render(containerRef.current, {
          sitekey: import.meta.env.VITE_TURNSTILE_SITE_KEY,
          callback: (token: string) => onToken(token),
          'expired-callback': () => onToken(null),
          'error-callback': () => onToken(null)
        })
      })
      .catch(() => onToken(null))

    return () => {
      cancelled = true
      if (widgetIdRef.current && window.turnstile) {
        window.turnstile.remove(widgetIdRef.current)
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return <div ref={containerRef} />
})

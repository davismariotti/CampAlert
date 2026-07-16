import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react'

interface TurnstileRenderOptions {
  sitekey: string
  appearance: 'interaction-only'
  callback: (token: string) => void
  'expired-callback': () => void
  'error-callback': () => void
  'unsupported-callback': () => void
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

// Most legitimate traffic resolves in well under this with interaction-only appearance (nothing
// visible, running in the background) — past this, treat it the same as an explicit error rather
// than leaving the user staring at a disabled button with zero feedback forever.
const LOAD_TIMEOUT_MS = 15_000

// Shared across every TurnstileWidget instance on the page — Cloudflare's own docs warn against
// fetching api.js more than once (it self-registers as `window.turnstile` on load). Cleared on
// failure so a retry actually re-attempts the network request instead of replaying a dead promise.
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
      script.onerror = () => {
        scriptLoadPromise = null
        reject(new Error('Failed to load Turnstile script'))
      }
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
 * Explicit-rendering Cloudflare Turnstile widget, `interaction-only` appearance — invisible for
 * the overwhelming majority of legitimate traffic, and only surfaces a visible challenge for
 * traffic Cloudflare actually flags (see design.md D4 in the add-turnstile-bot-protection change).
 * Explicit (not implicit) rendering is required because every form embedding this is React-managed,
 * not a static HTML <form> Turnstile's script can scan on page load.
 *
 * Because the widget is normally invisible, a load/network failure (blocked script, ad blocker,
 * firewall — unrelated to Cloudflare's own risk scoring) would otherwise look identical to the
 * normal "still resolving in the background" state: a disabled submit button with nothing on
 * screen to explain why. This component surfaces that failure explicitly instead of staying silent.
 */
export const TurnstileWidget = forwardRef<TurnstileWidgetHandle, Props>(function TurnstileWidget({ onToken }, ref) {
  const containerRef = useRef<HTMLDivElement>(null)
  const widgetIdRef = useRef<string | null>(null)
  const [failed, setFailed] = useState(false)
  const [retryCount, setRetryCount] = useState(0)

  useImperativeHandle(ref, () => ({
    reset: () => {
      onToken(null)
      setFailed(false)
      if (widgetIdRef.current && window.turnstile) {
        window.turnstile.reset(widgetIdRef.current)
      } else {
        // Widget never successfully rendered in the first place — a plain reset has nothing to
        // reset, so force the whole load sequence to run again instead.
        setRetryCount((n) => n + 1)
      }
    }
  }))

  useEffect(() => {
    let cancelled = false

    function fail() {
      if (cancelled) return
      setFailed(true)
      onToken(null)
    }

    const timeoutId = window.setTimeout(fail, LOAD_TIMEOUT_MS)

    loadTurnstileScript()
      .then(() => {
        if (cancelled || !containerRef.current || !window.turnstile) return
        widgetIdRef.current = window.turnstile.render(containerRef.current, {
          sitekey: import.meta.env.VITE_TURNSTILE_SITE_KEY,
          appearance: 'interaction-only',
          callback: (token: string) => {
            window.clearTimeout(timeoutId)
            onToken(token)
          },
          'expired-callback': () => onToken(null),
          'error-callback': fail,
          'unsupported-callback': fail
        })
      })
      .catch(fail)

    return () => {
      cancelled = true
      window.clearTimeout(timeoutId)
      if (widgetIdRef.current && window.turnstile) {
        window.turnstile.remove(widgetIdRef.current)
        widgetIdRef.current = null
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [retryCount])

  return (
    <div>
      <div ref={containerRef} />
      {failed && (
        <p className="mt-1 text-xs text-red-600">
          Verification failed to load. If you're using an ad blocker or privacy extension, try disabling it for this
          site, or{' '}
          <button
            type="button"
            className="underline hover:text-red-800"
            onClick={() => {
              setFailed(false)
              setRetryCount((n) => n + 1)
            }}
          >
            try again
          </button>
          .
        </p>
      )}
    </div>
  )
})

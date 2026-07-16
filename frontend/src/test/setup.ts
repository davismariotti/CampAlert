import '@testing-library/jest-dom'
import { forwardRef, useEffect, useImperativeHandle } from 'react'
import { vi } from 'vitest'

// Real Turnstile challenges can't run in jsdom (no network, no window.turnstile) — every test gets
// a widget that immediately reports a fake token, so submit buttons gated on turnstileToken behave
// the same as they would post-challenge. Mirrors the backend's use of Cloudflare's test keys
// (design.md D8 in the add-turnstile-bot-protection change) at the frontend layer.
vi.mock('../components/TurnstileWidget', () => ({
  TurnstileWidget: forwardRef<{ reset: () => void }, { onToken: (token: string | null) => void }>(
    function TurnstileWidget({ onToken }, ref) {
      useEffect(() => {
        onToken('test-turnstile-token')
        // eslint-disable-next-line react-hooks/exhaustive-deps
      }, [])
      useImperativeHandle(ref, () => ({ reset: () => onToken(null) }))
      return null
    }
  )
}))

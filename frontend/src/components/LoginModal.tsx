import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { login, resendVerification } from '../api/generated/sdk.gen'
import type { ErrorResponse } from '../api/generated/types.gen'
import { withoutRedirectOn401 } from '../api/client'
import { useAuth } from '../features/auth/useAuth'
import { useApiMutation } from '../hooks/useApiMutation'
import { Button } from './ui/Button'
import { Input } from './ui/Input'
import { TurnstileWidget, type TurnstileWidgetHandle } from './TurnstileWidget'
import type { AxiosError } from 'axios'

interface Props {
  onClose: () => void
}

export function LoginModal({ onClose }: Props) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [unverified, setUnverified] = useState(false)
  const [resendConfirmation, setResendConfirmation] = useState<string | null>(null)
  const [resendTurnstileToken, setResendTurnstileToken] = useState<string | null>(null)
  const { login: storeAuth } = useAuth()
  const navigate = useNavigate()
  const firstInputRef = useRef<HTMLInputElement>(null)
  const resendTurnstileRef = useRef<TurnstileWidgetHandle>(null)

  useEffect(() => {
    firstInputRef.current?.focus()
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  const mutation = useApiMutation({
    mutationFn: async () => {
      const result = await withoutRedirectOn401(() =>
        login({ body: { email, password, rememberMe }, throwOnError: true })
      )
      return result.data
    },
    onSuccess: (data) => {
      storeAuth(data)
      navigate('/')
    },
    onError: (err: AxiosError<ErrorResponse>) => {
      if (err.response?.status === 401) {
        if (err.response.data?.code === 'EMAIL_NOT_VERIFIED') {
          const verificationId = err.response.data.verificationId
          if (verificationId) {
            navigate(`/verify-email?verificationId=${verificationId}`, {
              state: { verificationId, email }
            })
            return
          }
          setUnverified(true)
          setError('Verify your email before signing in.')
        } else {
          setUnverified(false)
          setError('Invalid email or password')
        }
      } else if (err.response?.status === 400) {
        setError(err.response.data?.message ?? 'Validation error')
      } else {
        setError('Something went wrong. Please try again.')
      }
    }
  })

  const resendMutation = useApiMutation({
    mutationFn: async () => {
      await resendVerification({ body: { email, turnstileToken: resendTurnstileToken! }, throwOnError: true })
    },
    onSuccess: () => {
      setResendConfirmation('If that account can be verified, a new code has been sent.')
      setError(null)
    },
    onError: (err: AxiosError<ErrorResponse>) => {
      if (err.response?.data?.code === 'TURNSTILE_FAILED') {
        setError('Verification expired. Please try again.')
        resendTurnstileRef.current?.reset()
      } else {
        setError('Unable to request a new code. Please try again.')
      }
    }
  })

  const canSubmit = email.trim() !== '' && password !== ''

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-5"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div className="w-full max-w-sm rounded-2xl bg-white p-8 shadow-xl">
        {/* Header */}
        <div className="mb-6 flex items-start justify-between">
          <div className="flex items-center gap-2">
            <img src="/logo.png" alt="CampAlert" className="h-8 w-8 rounded-xl" />
            <span className="font-semibold text-forest-900">CampAlert</span>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-forest-400 hover:bg-forest-100 hover:text-forest-700"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        <h2 className="mb-5 text-xl font-semibold text-forest-900">Sign in</h2>

        <form
          className="flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault()
            setError(null)
            setUnverified(false)
            setResendConfirmation(null)
            mutation.mutate()
          }}
        >
          <Input
            ref={firstInputRef}
            type="email"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
          <Input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />

          <label className="flex items-center gap-2 text-sm text-forest-700 select-none cursor-pointer">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              className="h-4 w-4 rounded border-forest-300 accent-forest-700"
            />
            Remember me
          </label>

          {error && <p className="text-sm text-red-600">{error}</p>}
          {resendConfirmation && <p className="text-sm text-green-700">{resendConfirmation}</p>}

          {unverified && (
            <>
              <TurnstileWidget ref={resendTurnstileRef} onToken={setResendTurnstileToken} />
              <Button
                type="button"
                variant="secondary"
                loading={resendMutation.isPending}
                disabled={resendTurnstileToken === null}
                onClick={() => resendMutation.mutate()}
              >
                Resend verification email
              </Button>
            </>
          )}

          <Button type="submit" loading={mutation.isPending} disabled={!canSubmit}>
            Sign in
          </Button>
        </form>

        <p className="mt-4 text-center text-sm">
          <Link to="/forgot-password" className="font-medium text-forest-800 hover:underline">
            Forgot password?
          </Link>
        </p>

        <p className="mt-5 text-center text-sm text-forest-500">
          No account?{' '}
          <Link to="/register" className="font-medium text-forest-800 hover:underline">
            Create one
          </Link>
        </p>
      </div>
    </div>
  )
}

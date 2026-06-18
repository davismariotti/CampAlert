import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import type { AxiosError } from 'axios'
import { resendVerification, verifyEmail } from '../../api/generated/sdk.gen'
import type { ErrorResponse } from '../../api/generated/types.gen'
import { useAuth } from './useAuth'
import { useApiMutation } from '../../hooks/useApiMutation'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { AuthPageShell } from './AuthPageShell'

type VerificationLocationState = {
  verificationId?: string
  email?: string
}

const RESEND_COOLDOWN_SECONDS = 60

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const location = useLocation()
  const navigate = useNavigate()
  const { login: storeAuth } = useAuth()
  const locationState = location.state as VerificationLocationState | null
  const verificationId = searchParams.get('verificationId') ?? locationState?.verificationId ?? ''
  const urlCode = searchParams.get('code') ?? ''
  const [email, setEmail] = useState(locationState?.email ?? '')
  const [code, setCode] = useState(urlCode)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cooldown, setCooldown] = useState(0)

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = window.setInterval(() => setCooldown((value) => Math.max(0, value - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [cooldown])

  const verifyMutation = useApiMutation({
    mutationFn: async (codeToVerify: string) => {
      const result = await verifyEmail({ body: { verificationId, code: codeToVerify }, throwOnError: true })
      return result.data
    },
    onSuccess: (data) => {
      storeAuth(data)
      navigate('/', { replace: true })
    },
    onError: (err: AxiosError<ErrorResponse>) => {
      const errorCode = err.response?.data?.code
      if (errorCode === 'VERIFICATION_CODE_INVALID') {
        setError('That code is incorrect. Try again.')
      } else if (errorCode === 'VERIFICATION_CODE_ATTEMPTS_EXCEEDED') {
        setError('Too many incorrect attempts. Request a new code.')
      } else {
        setError('This verification request is invalid or expired. Request a new code.')
      }
    }
  })

  // Auto-submit when code is pre-filled from URL
  useEffect(() => {
    if (verificationId && urlCode && !verifyMutation.isPending && !verifyMutation.isSuccess) {
      verifyMutation.mutate(urlCode)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const resendMutation = useApiMutation({
    mutationFn: async () => {
      await resendVerification({ body: { email }, throwOnError: true })
    },
    onSuccess: () => {
      setMessage('If that account can be verified, a new code has been sent.')
      setError(null)
      setCooldown(RESEND_COOLDOWN_SECONDS)
    },
    onError: () => setError('Unable to request a new code. Please try again.')
  })

  // Show loading state while auto-verifying from link
  if (urlCode && verifyMutation.isPending) {
    return (
      <AuthPageShell>
        <p className="text-sm text-forest-600">Verifying your email…</p>
      </AuthPageShell>
    )
  }

  return (
    <AuthPageShell>
      <h1 className="mb-1 text-xl font-semibold text-forest-900">Verify your email</h1>
      <p className="mb-6 text-sm text-forest-500">Enter the 6-digit code from your verification email.</p>

      {verificationId ? (
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            setError(null)
            setMessage(null)
            verifyMutation.mutate(code)
          }}
        >
          <Input
            aria-label="Verification code"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            pattern="\d{6}"
            placeholder="123456"
            value={code}
            onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
          />
          <Button type="submit" loading={verifyMutation.isPending} disabled={!/^\d{6}$/.test(code)}>
            Verify email
          </Button>
        </form>
      ) : (
        <p className="mb-4 text-sm text-red-600">This verification link is missing its verification ID.</p>
      )}

      <div className="mt-6 border-t border-forest-100 pt-5">
        <p className="mb-3 text-sm text-forest-600">Need another code?</p>
        <div className="flex flex-col gap-3">
          <Input
            aria-label="Email for resend"
            type="email"
            autoComplete="email"
            placeholder="Email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
          <Button
            type="button"
            variant="secondary"
            loading={resendMutation.isPending}
            disabled={!email.trim() || cooldown > 0}
            onClick={() => resendMutation.mutate()}
          >
            {cooldown > 0 ? `Resend available in ${cooldown}s` : 'Resend verification email'}
          </Button>
        </div>
      </div>

      {message && <p className="mt-4 text-sm text-green-700">{message}</p>}
      {error && <p className="mt-4 text-sm text-red-600">{error}</p>}

      <p className="mt-5 text-center text-sm text-forest-500">
        <Link to="/?login=1" className="font-medium text-forest-800 hover:underline">
          Back to sign in
        </Link>
      </p>
    </AuthPageShell>
  )
}

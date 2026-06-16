import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import type { AxiosError } from 'axios'
import { resetPassword } from '../../api/generated/sdk.gen'
import type { ErrorResponse } from '../../api/generated/types.gen'
import { useApiMutation } from '../../hooks/useApiMutation'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { AuthPageShell } from './AuthPageShell'

export function ResetPasswordPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const resetId = searchParams.get('resetId') ?? ''
  const token = searchParams.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [succeeded, setSucceeded] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const mutation = useApiMutation({
    mutationFn: async () => {
      await resetPassword({
        body: { resetId, token, newPassword: password },
        throwOnError: true
      })
    },
    onSuccess: () => {
      setSubmitted(true)
      setSearchParams({}, { replace: true })
      setSucceeded(true)
      setError(null)
    },
    onError: (err: AxiosError<ErrorResponse>) => {
      const errorCode = err.response?.data?.code
      if (errorCode === 'RESET_PASSWORD_SAME_AS_CURRENT') {
        setError('Choose a password different from your current password.')
      } else if (errorCode === 'RESET_PASSWORD_TOO_WEAK') {
        setError('Your new password does not meet the password requirements.')
      } else {
        setError('This password reset link is invalid or expired.')
      }
    }
  })

  const hasResetParams = Boolean(resetId && token)
  const passwordsMatch = password === confirmation
  const validPassword = password.length >= 8 && password.length <= 72

  return (
    <AuthPageShell>
      <h1 className="mb-1 text-xl font-semibold text-forest-900">Reset your password</h1>
      <p className="mb-6 text-sm text-forest-500">Choose a new password between 8 and 72 characters.</p>

      {succeeded ? (
        <div>
          <p className="rounded-xl bg-green-50 p-4 text-sm text-green-800">Your password has been reset.</p>
          <Link
            to="/?login=1"
            className="mt-4 inline-flex w-full justify-center rounded-xl bg-forest-800 px-4 py-2 text-sm font-medium text-white hover:bg-forest-700"
          >
            Continue to sign in
          </Link>
        </div>
      ) : (
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            setError(null)
            mutation.mutate()
          }}
        >
          <Input
            aria-label="New password"
            type="password"
            autoComplete="new-password"
            placeholder="New password"
            minLength={8}
            maxLength={72}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={submitted || mutation.isPending}
          />
          <Input
            aria-label="Confirm new password"
            type="password"
            autoComplete="new-password"
            placeholder="Confirm new password"
            minLength={8}
            maxLength={72}
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
            disabled={submitted || mutation.isPending}
          />
          {!hasResetParams && !submitted && (
            <p className="text-sm text-red-600">This password reset link is missing required information.</p>
          )}
          {confirmation && !passwordsMatch && <p className="text-sm text-red-600">Passwords do not match.</p>}
          {error && <p className="text-sm text-red-600">{error}</p>}
          <Button
            type="submit"
            loading={mutation.isPending}
            disabled={submitted || !hasResetParams || !validPassword || !passwordsMatch}
          >
            Reset password
          </Button>
        </form>
      )}
    </AuthPageShell>
  )
}

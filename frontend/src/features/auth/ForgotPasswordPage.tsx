import { useState } from 'react'
import { Link } from 'react-router-dom'
import { forgotPassword } from '../../api/generated/sdk.gen'
import { useApiMutation } from '../../hooks/useApiMutation'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { AuthPageShell } from './AuthPageShell'

const ACCEPTED_MESSAGE = 'If an eligible account exists, a password reset link has been sent.'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const mutation = useApiMutation({
    mutationFn: async () => {
      await forgotPassword({ body: { email }, throwOnError: true })
    },
    onSuccess: () => {
      setSubmitted(true)
      setError(null)
    },
    onError: () => setError('Unable to submit the request. Please try again.')
  })

  return (
    <AuthPageShell>
      <h1 className="mb-1 text-xl font-semibold text-forest-900">Forgot your password?</h1>
      <p className="mb-6 text-sm text-forest-500">Enter your email and we will send recovery instructions.</p>

      {submitted ? (
        <p className="rounded-xl bg-green-50 p-4 text-sm text-green-800">{ACCEPTED_MESSAGE}</p>
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
            aria-label="Email"
            type="email"
            autoComplete="email"
            placeholder="Email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
          {error && <p className="text-sm text-red-600">{error}</p>}
          <Button type="submit" loading={mutation.isPending} disabled={!email.trim()}>
            Send reset link
          </Button>
        </form>
      )}

      <p className="mt-5 text-center text-sm text-forest-500">
        <Link to="/?login=1" className="font-medium text-forest-800 hover:underline">
          Back to sign in
        </Link>
      </p>
    </AuthPageShell>
  )
}

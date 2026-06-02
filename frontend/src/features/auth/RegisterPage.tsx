import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { register } from '../../api/generated/sdk.gen'
import { useAuth } from './useAuth'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import type { AxiosError } from 'axios'

export function RegisterPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { login: storeAuth } = useAuth()
  const navigate = useNavigate()

  const mutation = useMutation({
    mutationFn: () => register({ body: { email, password } }),
    onSuccess: ({ data }) => {
      if (data) {
        storeAuth(data)
        navigate('/')
      }
    },
    onError: (err: AxiosError<{ message: string }>) => {
      if (err.response?.status === 409) {
        setError('An account with this email already exists')
      } else if (err.response?.status === 400) {
        setError(err.response.data?.message ?? 'Validation error')
      } else {
        setError('Something went wrong. Please try again.')
      }
    }
  })

  const canSubmit = email.trim() !== '' && password !== ''

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="w-full max-w-sm rounded-2xl bg-white p-8 shadow-md">
        <div className="mb-6 flex flex-col items-center gap-2">
          <img src="/logo.png" alt="CampAlert" className="h-12 w-12 rounded" />
          <h1 className="text-xl font-semibold text-forest-900">Create your account</h1>
        </div>

        <form
          className="flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault()
            setError(null)
            mutation.mutate()
          }}
        >
          <Input
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
            autoComplete="new-password"
            required
          />

          {error && <p className="text-sm text-red-600">{error}</p>}

          <Button type="submit" loading={mutation.isPending} disabled={!canSubmit}>
            Create account
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-forest-600">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-forest-800 hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}

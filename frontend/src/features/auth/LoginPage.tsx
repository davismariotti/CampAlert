import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useApiMutation } from '../../hooks/useApiMutation'
import { login } from '../../api/generated/sdk.gen'
import { useAuth } from './useAuth'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import type { AxiosError } from 'axios'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { login: storeAuth } = useAuth()
  const navigate = useNavigate()

  const mutation = useApiMutation({
    mutationFn: async () => {
      const result = await login({ body: { email, password } })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: (data) => {
      storeAuth(data)
      navigate('/')
    },
    onError: (err: AxiosError<{ message: string }>) => {
      if (err.response?.status === 401) {
        setError('Invalid email or password')
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
          <h1 className="text-xl font-semibold text-forest-900">Sign in to CampAlert</h1>
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
            autoComplete="current-password"
            required
          />

          {error && <p className="text-sm text-red-600">{error}</p>}

          <Button type="submit" loading={mutation.isPending} disabled={!canSubmit}>
            Sign in
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-forest-600">
          No account?{' '}
          <Link to="/register" className="font-medium text-forest-800 hover:underline">
            Create one
          </Link>
        </p>
      </div>
    </div>
  )
}

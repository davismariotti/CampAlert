import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { login } from '../api/generated/sdk.gen'
import { useAuth } from '../features/auth/useAuth'
import { useApiMutation } from '../hooks/useApiMutation'
import { Button } from './ui/Button'
import { Input } from './ui/Input'
import type { AxiosError } from 'axios'

interface Props {
  onClose: () => void
}

export function LoginModal({ onClose }: Props) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { login: storeAuth } = useAuth()
  const navigate = useNavigate()
  const firstInputRef = useRef<HTMLInputElement>(null)

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

          {error && <p className="text-sm text-red-600">{error}</p>}

          <Button type="submit" loading={mutation.isPending} disabled={!canSubmit}>
            Sign in
          </Button>
        </form>

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

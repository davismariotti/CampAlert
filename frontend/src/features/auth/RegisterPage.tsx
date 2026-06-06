import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useApiMutation } from '../../hooks/useApiMutation'
import { register } from '../../api/generated/sdk.gen'
import { useAuth } from './useAuth'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import landscapeImg from '../../assets/landscape.jpg'
import { getBrowserTimezone, getTimezoneOptions } from '../../utils/timezones'
import type { AxiosError } from 'axios'

export function RegisterPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [timezone, setTimezone] = useState(getBrowserTimezone)
  const [error, setError] = useState<string | null>(null)
  const { login: storeAuth } = useAuth()
  const navigate = useNavigate()
  const timezoneOptions = getTimezoneOptions()

  const mutation = useApiMutation({
    mutationFn: async () => {
      const result = await register({ body: { email, password, timezone } })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: (data) => {
      storeAuth(data)
      navigate('/requests')
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
    <div className="min-h-screen bg-stone-50">
      {/* Landscape banner */}
      <div
        className="relative flex h-48 items-center justify-center"
        style={{
          backgroundImage: `url(${landscapeImg})`,
          backgroundSize: 'cover',
          backgroundPosition: 'center 55%'
        }}
      >
        <div className="absolute inset-0 bg-forest-950/65" />
        <Link to="/" className="relative z-10 flex flex-col items-center gap-2">
          <img src="/logo.png" alt="CampAlert" className="h-12 w-12 rounded-2xl" />
          <span className="text-lg font-bold text-white">CampAlert</span>
        </Link>
      </div>

      {/* Form */}
      <div className="px-5 py-10">
        <div className="mx-auto max-w-sm rounded-2xl bg-white p-8 shadow-sm">
          <h1 className="mb-1 text-xl font-semibold text-forest-900">Create your account</h1>
          <p className="mb-6 text-sm text-forest-500">Free to use. No credit card needed.</p>

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
            <label className="flex flex-col gap-1 text-sm font-medium text-forest-700">
              Timezone
              <select
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
                className="w-full rounded-xl border border-forest-200 bg-white px-3 py-2 text-sm text-forest-900 focus:border-forest-500 focus:outline-none focus:ring-2 focus:ring-forest-500/30"
                required
              >
                {timezoneOptions.map((zone) => (
                  <option key={zone} value={zone}>
                    {zone.replace('_', ' ')}
                  </option>
                ))}
              </select>
            </label>

            {error && <p className="text-sm text-red-600">{error}</p>}

            <Button type="submit" loading={mutation.isPending} disabled={!canSubmit}>
              Create account
            </Button>
          </form>

          <p className="mt-5 text-center text-sm text-forest-500">
            Already have an account?{' '}
            <Link to="/login" className="font-medium text-forest-800 hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}

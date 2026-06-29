import { useState } from 'react'
import { Link } from 'react-router-dom'
import { changePassword } from '../../api/generated/sdk.gen'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'

export function ChangePasswordForm() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [confirmError, setConfirmError] = useState<string | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [isPending, setIsPending] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setServerError(null)
    setConfirmError(null)

    if (newPassword !== confirmPassword) {
      setConfirmError('Passwords do not match.')
      return
    }

    setIsPending(true)
    try {
      const result = await changePassword({
        body: { currentPassword, newPassword }
      })

      if (result.response.status === 403) {
        setServerError('Request could not be completed — please refresh and try again.')
      } else if (result.error) {
        const err = result.error as { message?: string }
        setServerError(err.message ?? 'Something went wrong. Please try again.')
      } else {
        setSuccess(true)
        setCurrentPassword('')
        setNewPassword('')
        setConfirmPassword('')
      }
    } finally {
      setIsPending(false)
    }
  }

  return (
    <form className="mt-5 flex flex-col gap-4" onSubmit={handleSubmit}>
      {success && <p className="rounded-xl bg-green-50 p-3 text-sm text-green-800">Password updated successfully.</p>}

      <label className="flex flex-col gap-1 text-sm font-medium text-forest-700">
        Current password
        <Input
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          onChange={(e) => {
            setCurrentPassword(e.target.value)
            setSuccess(false)
          }}
          required
        />
      </label>

      <label className="flex flex-col gap-1 text-sm font-medium text-forest-700">
        New password
        <Input
          type="password"
          autoComplete="new-password"
          value={newPassword}
          onChange={(e) => {
            setNewPassword(e.target.value)
            setSuccess(false)
          }}
          required
          minLength={8}
        />
      </label>

      <label className="flex flex-col gap-1 text-sm font-medium text-forest-700">
        Confirm new password
        <Input
          type="password"
          autoComplete="new-password"
          value={confirmPassword}
          onChange={(e) => {
            setConfirmPassword(e.target.value)
            setConfirmError(null)
            setSuccess(false)
          }}
          required
        />
      </label>

      {confirmError && <p className="text-sm text-red-600">{confirmError}</p>}
      {serverError && <p className="text-sm text-red-600">{serverError}</p>}

      <div className="flex items-center justify-between gap-4">
        <Button type="submit" loading={isPending} disabled={!currentPassword || !newPassword || !confirmPassword}>
          Update password
        </Button>
        <Link to="/forgot-password" className="text-sm text-forest-600 hover:text-forest-900 hover:underline">
          Forgot your current password?
        </Link>
      </div>
    </form>
  )
}

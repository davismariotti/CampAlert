import { useState } from 'react'
import { updateMe } from '../../api/generated/sdk.gen'
import { Button } from '../../components/ui/Button'
import { useApiMutation } from '../../hooks/useApiMutation'
import { useAuth } from '../auth/useAuth'
import { DEFAULT_TIMEZONE, getTimezoneOptions } from '../../utils/timezones'
import { ChangePasswordForm } from './ChangePasswordForm'

export function AccountSettingsPage() {
  const { user, login } = useAuth()
  const [timezone, setTimezone] = useState(user?.timezone ?? DEFAULT_TIMEZONE)
  const [saved, setSaved] = useState(false)
  const timezoneOptions = getTimezoneOptions()

  const mutation = useApiMutation({
    mutationFn: async () => {
      const result = await updateMe({ body: { timezone } })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: (data) => {
      login(data)
      setSaved(true)
    },
    errorMessage: 'Failed to update account settings. Please try again.'
  })

  const unchanged = timezone === (user?.timezone ?? DEFAULT_TIMEZONE)

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-forest-900">Account Settings</h1>
        <p className="mt-1 text-sm text-forest-600">{user?.email}</p>
      </div>

      <section className="rounded-2xl bg-white p-6 shadow-sm">
        <h2 className="text-base font-semibold text-forest-900">SMS quiet hours</h2>
        <p className="mt-1 text-sm text-forest-600">
          Your timezone controls when SMS alerts are held during quiet hours from 1am to 6am.
        </p>

        <form
          className="mt-5 flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault()
            setSaved(false)
            mutation.mutate()
          }}
        >
          <label className="flex flex-col gap-1 text-sm font-medium text-forest-700">
            Timezone
            <select
              value={timezone}
              onChange={(e) => {
                setTimezone(e.target.value)
                setSaved(false)
              }}
              className="w-full rounded-xl border border-forest-200 bg-white px-3 py-2 text-sm text-forest-900 focus:border-forest-500 focus:outline-none focus:ring-2 focus:ring-forest-500/30"
            >
              {timezoneOptions.map((zone) => (
                <option key={zone} value={zone}>
                  {zone.replace('_', ' ')}
                </option>
              ))}
            </select>
          </label>

          {saved && <p className="text-sm text-forest-600">Settings saved.</p>}

          <div>
            <Button type="submit" loading={mutation.isPending} disabled={unchanged}>
              Save settings
            </Button>
          </div>
        </form>
      </section>

      <section className="mt-6 rounded-2xl bg-white p-6 shadow-sm">
        <h2 className="text-base font-semibold text-forest-900">Security</h2>
        <p className="mt-1 text-sm text-forest-600">
          Update your password. You'll need your current password to make changes.
        </p>
        <ChangePasswordForm />
      </section>
    </div>
  )
}

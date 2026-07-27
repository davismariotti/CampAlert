import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  adminGetGlobalSettings,
  adminSetGlobalQuota,
  adminSetGlobalProviderQuota,
  adminClearGlobalProviderQuota,
  adminSetGlobalProviderAccess
} from '../../api/generated/sdk.gen'
import type { AdminGlobalSettingsResponse, ProviderType } from '../../api/generated/types.gen'
import { useApiMutation } from '../../hooks/useApiMutation'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Toggle } from '../../components/ui/Toggle'
import { providerName, ALL_PROVIDERS } from '../../utils/providers'

const QUERY_KEY = ['admin-global-settings']

function CombinedQuotaRow({ settings }: { settings: AdminGlobalSettingsResponse }) {
  const queryClient = useQueryClient()
  const [value, setValue] = useState(String(settings.combinedMaxActive))

  const mutation = useApiMutation({
    mutationFn: async () => {
      const result = await adminSetGlobalQuota({ body: { maxActive: Number(value) } })
      if (result.error) throw result
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEY }),
    errorMessage: 'Failed to set global quota. Please try again.'
  })

  return (
    <div className="rounded-2xl bg-white p-5 shadow-sm">
      <h2 className="mb-3 text-sm font-semibold text-forest-900">Default combined active alert limit</h2>
      <div className="flex items-center gap-2">
        <Input type="number" min={0} value={value} onChange={(e) => setValue(e.target.value)} className="max-w-24" />
        <Button
          variant="secondary"
          loading={mutation.isPending}
          disabled={value === ''}
          onClick={() => mutation.mutate()}
        >
          Save
        </Button>
      </div>
    </div>
  )
}

function ProviderRow({ provider, settings }: { provider: ProviderType; settings: AdminGlobalSettingsResponse }) {
  const queryClient = useQueryClient()
  const quota = settings.providerQuotas.find((q) => q.provider === provider)
  const access = settings.providerAccess.find((a) => a.provider === provider)
  const [quotaValue, setQuotaValue] = useState(quota?.maxActive != null ? String(quota.maxActive) : '')

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: QUERY_KEY })
  }

  const setAccessMutation = useApiMutation({
    mutationFn: async (enabled: boolean) => {
      const result = await adminSetGlobalProviderAccess({ path: { provider }, body: { enabled } })
      if (result.error) throw result
    },
    onSuccess: invalidate,
    errorMessage: 'Failed to set provider access. Please try again.'
  })

  const setQuotaMutation = useApiMutation({
    mutationFn: async () => {
      const result = await adminSetGlobalProviderQuota({ path: { provider }, body: { maxActive: Number(quotaValue) } })
      if (result.error) throw result
    },
    onSuccess: invalidate,
    errorMessage: 'Failed to set provider quota. Please try again.'
  })

  const clearQuotaMutation = useApiMutation({
    mutationFn: async () => {
      const result = await adminClearGlobalProviderQuota({ path: { provider } })
      if (result.error) throw result
    },
    onSuccess: () => {
      setQuotaValue('')
      invalidate()
    },
    errorMessage: 'Failed to clear provider quota. Please try again.'
  })

  return (
    <div className="rounded-xl border border-forest-200 p-4">
      <h3 className="mb-3 text-sm font-semibold text-forest-900">{providerName(provider)}</h3>

      <div className="mb-3 flex items-center gap-2">
        <Toggle
          checked={access?.enabled ?? false}
          disabled={setAccessMutation.isPending}
          onChange={(checked) => setAccessMutation.mutate(checked)}
          label={access?.enabled ? 'Enabled by default' : 'Disabled by default'}
        />
      </div>

      <div className="flex items-center gap-2">
        <Input
          type="number"
          min={0}
          placeholder="Uncapped"
          value={quotaValue}
          onChange={(e) => setQuotaValue(e.target.value)}
          className="max-w-24"
        />
        <Button
          variant="secondary"
          loading={setQuotaMutation.isPending}
          disabled={quotaValue === ''}
          onClick={() => setQuotaMutation.mutate()}
        >
          Save
        </Button>
        {quota?.maxActive != null && (
          <button
            type="button"
            disabled={clearQuotaMutation.isPending}
            onClick={() => clearQuotaMutation.mutate()}
            className="text-xs font-medium text-red-600 hover:underline disabled:opacity-50"
          >
            Uncap
          </button>
        )}
      </div>
    </div>
  )
}

export function AdminGlobalSettingsTab() {
  const { data, isLoading, isError } = useQuery({
    queryKey: QUERY_KEY,
    queryFn: async () => {
      const result = await adminGetGlobalSettings()
      if (result.error) throw result
      return result.data!
    }
  })

  if (isLoading) return <p className="text-sm text-forest-500">Loading…</p>
  if (isError || !data) return <p className="text-sm text-red-600">Failed to load global settings.</p>

  return (
    <div className="flex flex-col gap-6">
      <p className="text-sm text-forest-500">
        These are the fallback values used when a user has no per-user or group override. Changes here take effect for
        affected users the next time their requests are checked, not immediately.
      </p>
      <CombinedQuotaRow settings={data} />
      <div>
        <h2 className="mb-3 text-sm font-semibold text-forest-900">Per-provider defaults</h2>
        <div className="flex flex-col gap-3">
          {ALL_PROVIDERS.map((p) => (
            <ProviderRow key={p.type} provider={p.type} settings={data} />
          ))}
        </div>
      </div>
    </div>
  )
}

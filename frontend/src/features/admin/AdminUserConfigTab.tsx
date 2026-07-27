import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  adminGetUser,
  adminListGroups,
  adminAddUserToGroup,
  adminRemoveUserFromGroup,
  adminSetUserQuota,
  adminClearUserQuota,
  adminSetUserProviderQuota,
  adminClearUserProviderQuota,
  adminSetUserProviderAccess,
  adminClearUserProviderAccess
} from '../../api/generated/sdk.gen'
import type { AdminEffectiveValueSource, AdminUserDetailResponse, ProviderType } from '../../api/generated/types.gen'
import { useApiMutation } from '../../hooks/useApiMutation'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Toggle } from '../../components/ui/Toggle'
import { providerName, ALL_PROVIDERS } from '../../utils/providers'

function sourceLabel(source: AdminEffectiveValueSource): string {
  switch (source) {
    case 'USER_OVERRIDE':
      return 'per-user override'
    case 'GROUP_DEFAULT':
      return 'group default'
    case 'GLOBAL_DEFAULT':
      return 'global default'
  }
}

function SourceBadge({ source }: { source: AdminEffectiveValueSource }) {
  return (
    <span className="rounded-full bg-forest-100 px-2 py-0.5 text-xs font-medium text-forest-600">
      {sourceLabel(source)}
    </span>
  )
}

function GroupMembershipSection({ user, userId }: { user: AdminUserDetailResponse; userId: number }) {
  const queryClient = useQueryClient()
  const [selectedGroupId, setSelectedGroupId] = useState<number | ''>('')

  const { data: allGroups } = useQuery({
    queryKey: ['admin-groups'],
    queryFn: async () => {
      const result = await adminListGroups()
      if (result.error) throw result
      return result.data ?? []
    }
  })

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['admin-user', userId] })
  }

  const addMutation = useApiMutation({
    mutationFn: async (groupId: number) => {
      const result = await adminAddUserToGroup({ path: { id: userId, groupId } })
      if (result.error) throw result
    },
    onSuccess: () => {
      setSelectedGroupId('')
      invalidate()
    },
    errorMessage: 'Failed to add to group. Please try again.'
  })

  const removeMutation = useApiMutation({
    mutationFn: async (groupId: number) => {
      const result = await adminRemoveUserFromGroup({ path: { id: userId, groupId } })
      if (result.error) throw result
    },
    onSuccess: invalidate,
    errorMessage: 'Failed to remove from group. Please try again.'
  })

  const availableGroups = (allGroups ?? []).filter((g) => !user.groups.some((ug) => ug.id === g.id))

  return (
    <div className="rounded-2xl bg-white p-5 shadow-sm">
      <h2 className="mb-3 text-sm font-semibold text-forest-900">Group membership</h2>
      <ul className="mb-4 flex flex-col gap-2">
        {user.groups.length === 0 && <li className="text-sm text-forest-400">Not a member of any group.</li>}
        {user.groups.map((g) => (
          <li key={g.id} className="flex items-center justify-between rounded-lg bg-forest-50 px-3 py-2 text-sm">
            <span className="font-medium text-forest-900">{g.groupName}</span>
            <button
              type="button"
              disabled={removeMutation.isPending}
              onClick={() => removeMutation.mutate(g.id)}
              className="text-xs font-medium text-red-600 hover:underline disabled:opacity-50"
            >
              Remove
            </button>
          </li>
        ))}
      </ul>
      <div className="flex gap-2">
        <select
          value={selectedGroupId}
          onChange={(e) => setSelectedGroupId(e.target.value ? Number(e.target.value) : '')}
          className="w-full rounded-xl border border-forest-200 bg-white px-3 py-2 text-sm text-forest-900 focus:border-forest-500 focus:outline-none focus:ring-2 focus:ring-forest-500/30"
        >
          <option value="">Add to group…</option>
          {availableGroups.map((g) => (
            <option key={g.id} value={g.id}>
              {g.groupName}
            </option>
          ))}
        </select>
        <Button
          variant="secondary"
          disabled={selectedGroupId === '' || addMutation.isPending}
          loading={addMutation.isPending}
          onClick={() => selectedGroupId !== '' && addMutation.mutate(selectedGroupId)}
        >
          Add
        </Button>
      </div>
    </div>
  )
}

function CombinedQuotaSection({ user, userId }: { user: AdminUserDetailResponse; userId: number }) {
  const queryClient = useQueryClient()
  const [value, setValue] = useState(String(user.effectiveCombinedQuota.value))

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['admin-user', userId] })
  }

  const setMutation = useApiMutation({
    mutationFn: async () => {
      const result = await adminSetUserQuota({ path: { id: userId }, body: { maxActive: Number(value) } })
      if (result.error) throw result
    },
    onSuccess: invalidate,
    errorMessage: 'Failed to set quota. Please try again.'
  })

  const clearMutation = useApiMutation({
    mutationFn: async () => {
      const result = await adminClearUserQuota({ path: { id: userId } })
      if (result.error) throw result
    },
    onSuccess: invalidate,
    errorMessage: 'Failed to clear quota override. Please try again.'
  })

  const isOverridden = user.effectiveCombinedQuota.source === 'USER_OVERRIDE'

  return (
    <div className="rounded-2xl bg-white p-5 shadow-sm">
      <div className="mb-3 flex items-center gap-2">
        <h2 className="text-sm font-semibold text-forest-900">Combined active alert limit</h2>
        <SourceBadge source={user.effectiveCombinedQuota.source} />
      </div>
      <div className="flex items-center gap-2">
        <Input type="number" min={0} value={value} onChange={(e) => setValue(e.target.value)} className="max-w-24" />
        <Button
          variant="secondary"
          loading={setMutation.isPending}
          disabled={value === ''}
          onClick={() => setMutation.mutate()}
        >
          Save
        </Button>
        {isOverridden && (
          <Button variant="ghost" loading={clearMutation.isPending} onClick={() => clearMutation.mutate()}>
            Clear override
          </Button>
        )}
      </div>
    </div>
  )
}

function ProviderOverrideRow({
  provider,
  user,
  userId
}: {
  provider: ProviderType
  user: AdminUserDetailResponse
  userId: number
}) {
  const queryClient = useQueryClient()
  const quota = user.effectiveProviderQuotas.find((q) => q.provider === provider)!
  const access = user.effectiveProviderAccess.find((a) => a.provider === provider)!
  const [quotaValue, setQuotaValue] = useState(quota.value != null ? String(quota.value) : '')

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['admin-user', userId] })
  }

  const setAccessMutation = useApiMutation({
    mutationFn: async (enabled: boolean) => {
      const result = await adminSetUserProviderAccess({ path: { id: userId, provider }, body: { enabled } })
      if (result.error) throw result
    },
    onSuccess: invalidate,
    errorMessage: 'Failed to set provider access. Please try again.'
  })

  const clearAccessMutation = useApiMutation({
    mutationFn: async () => {
      const result = await adminClearUserProviderAccess({ path: { id: userId, provider } })
      if (result.error) throw result
    },
    onSuccess: invalidate,
    errorMessage: 'Failed to clear provider access override. Please try again.'
  })

  const setQuotaMutation = useApiMutation({
    mutationFn: async () => {
      const result = await adminSetUserProviderQuota({
        path: { id: userId, provider },
        body: { maxActive: Number(quotaValue) }
      })
      if (result.error) throw result
    },
    onSuccess: invalidate,
    errorMessage: 'Failed to set provider quota. Please try again.'
  })

  const clearQuotaMutation = useApiMutation({
    mutationFn: async () => {
      const result = await adminClearUserProviderQuota({ path: { id: userId, provider } })
      if (result.error) throw result
    },
    onSuccess: invalidate,
    errorMessage: 'Failed to clear provider quota override. Please try again.'
  })

  return (
    <div className="rounded-xl border border-forest-200 p-4">
      <h3 className="mb-3 text-sm font-semibold text-forest-900">{providerName(provider)}</h3>

      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Toggle
            checked={access.value}
            disabled={setAccessMutation.isPending}
            onChange={(checked) => setAccessMutation.mutate(checked)}
          />
          <SourceBadge source={access.source} />
        </div>
        {access.source === 'USER_OVERRIDE' && (
          <button
            type="button"
            disabled={clearAccessMutation.isPending}
            onClick={() => clearAccessMutation.mutate()}
            className="text-xs font-medium text-red-600 hover:underline disabled:opacity-50"
          >
            Clear override
          </button>
        )}
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
        <SourceBadge source={quota.source} />
        {quota.source === 'USER_OVERRIDE' && (
          <button
            type="button"
            disabled={clearQuotaMutation.isPending}
            onClick={() => clearQuotaMutation.mutate()}
            className="text-xs font-medium text-red-600 hover:underline disabled:opacity-50"
          >
            Clear override
          </button>
        )}
      </div>
    </div>
  )
}

export function AdminUserConfigTab({ userId }: { userId: number }) {
  const {
    data: user,
    isLoading,
    isError
  } = useQuery({
    queryKey: ['admin-user', userId],
    queryFn: async () => {
      const result = await adminGetUser({ path: { id: userId } })
      if (result.error) throw result
      return result.data!
    }
  })

  if (isLoading) return <p className="text-sm text-forest-500">Loading…</p>
  if (isError || !user) return <p className="text-sm text-red-600">Failed to load user config.</p>

  return (
    <div className="flex flex-col gap-6">
      <GroupMembershipSection user={user} userId={userId} />
      <CombinedQuotaSection user={user} userId={userId} />
      <div>
        <h2 className="mb-3 text-sm font-semibold text-forest-900">Per-provider overrides</h2>
        <div className="flex flex-col gap-3">
          {ALL_PROVIDERS.map((p) => (
            <ProviderOverrideRow key={p.type} provider={p.type} user={user} userId={userId} />
          ))}
        </div>
      </div>
    </div>
  )
}

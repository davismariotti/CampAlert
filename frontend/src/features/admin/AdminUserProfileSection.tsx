import type { AdminUserDetailResponse, AdminEffectiveValueSource } from '../../api/generated/types.gen'
import { providerName } from '../../utils/providers'

function formatDateTime(dateStr: string | null | undefined) {
  if (!dateStr) return 'Never'
  return new Date(dateStr).toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

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

export function AdminUserProfileSection({ user }: { user: AdminUserDetailResponse }) {
  return (
    <div className="flex flex-col gap-6">
      <div className="rounded-2xl bg-white p-5 shadow-sm">
        <h2 className="mb-3 text-sm font-semibold text-forest-900">Profile</h2>
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div>
            <dt className="text-xs font-medium text-forest-500">Email</dt>
            <dd className="text-forest-900">{user.email}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-forest-500">Timezone</dt>
            <dd className="text-forest-900">{user.timezone}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-forest-500">Email verified</dt>
            <dd className="text-forest-900">{formatDateTime(user.emailVerifiedAt)}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-forest-500">Last login</dt>
            <dd className="text-forest-900">{formatDateTime(user.lastLoginAt)}</dd>
          </div>
          <div className="col-span-2">
            <dt className="text-xs font-medium text-forest-500">Groups</dt>
            <dd className="mt-1 flex flex-wrap gap-1.5">
              {user.groups.length === 0 && <span className="text-forest-400">None</span>}
              {user.groups.map((g) => (
                <span key={g.id} className="rounded-full bg-forest-700 px-2.5 py-0.5 text-xs font-medium text-white">
                  {g.groupName}
                </span>
              ))}
            </dd>
          </div>
        </dl>
      </div>

      <div className="rounded-2xl bg-white p-5 shadow-sm">
        <h2 className="mb-3 text-sm font-semibold text-forest-900">Phone numbers</h2>
        {user.phoneNumbers.length === 0 && <p className="text-sm text-forest-400">No phone numbers.</p>}
        {user.phoneNumbers.length > 0 && (
          <ul className="flex flex-col gap-2">
            {user.phoneNumbers.map((p) => (
              <li key={p.id} className="flex items-center justify-between text-sm">
                <span className="text-forest-900">{p.phone}</span>
                <span className="rounded-full bg-forest-100 px-2 py-0.5 text-xs font-medium text-forest-600">
                  {p.status}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="rounded-2xl bg-white p-5 shadow-sm">
        <h2 className="mb-3 text-sm font-semibold text-forest-900">Effective quota &amp; provider access</h2>
        <div className="mb-3 flex items-center justify-between text-sm">
          <span className="text-forest-700">Combined active alert limit</span>
          <span className="flex items-center gap-2 font-medium text-forest-900">
            {user.effectiveCombinedQuota.value}
            <SourceBadge source={user.effectiveCombinedQuota.source} />
          </span>
        </div>
        <div className="overflow-x-auto rounded-xl border border-forest-200">
          <table className="w-full text-left text-sm">
            <thead className="bg-forest-50 text-xs font-medium uppercase text-forest-500">
              <tr>
                <th className="px-4 py-2">Provider</th>
                <th className="px-4 py-2">Access</th>
                <th className="px-4 py-2">Per-provider limit</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-forest-100">
              {user.effectiveProviderQuotas.map((quota) => {
                const access = user.effectiveProviderAccess.find((a) => a.provider === quota.provider)
                return (
                  <tr key={quota.provider}>
                    <td className="px-4 py-2 font-medium text-forest-900">{providerName(quota.provider)}</td>
                    <td className="px-4 py-2">
                      {access && (
                        <span className="flex items-center gap-2">
                          <span className={access.value ? 'text-forest-700' : 'text-neutral-400'}>
                            {access.value ? 'Enabled' : 'Disabled'}
                          </span>
                          <SourceBadge source={access.source} />
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-2">
                      <span className="flex items-center gap-2 text-forest-700">
                        {quota.value ?? 'Uncapped'}
                        <SourceBadge source={quota.source} />
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

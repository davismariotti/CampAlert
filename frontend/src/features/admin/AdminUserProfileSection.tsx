import type { AdminUserDetailResponse } from '../../api/generated/types.gen'

function formatDateTime(dateStr: string | null | undefined) {
  if (!dateStr) return 'Never'
  return new Date(dateStr).toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

// Effective quota/provider-access values are shown (and editable) in AdminUserConfigSection below —
// not duplicated here as a separate read-only summary, since both now render on the same page.
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
    </div>
  )
}

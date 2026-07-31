import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { canManageInvites } from '../auth/permissions'
import { Button } from '../../components/ui/Button'
import { AdminOverviewTab } from './AdminOverviewTab'
import { AdminUsersTab } from './AdminUsersTab'
import { AdminGlobalSettingsTab } from './AdminGlobalSettingsTab'
import { AdminAuditLogTab } from './AdminAuditLogTab'
import { AdminInvitesTab } from './AdminInvitesTab'
import { InviteModal } from './InviteModal'

type Tab = 'overview' | 'users' | 'global-settings' | 'audit-log' | 'invites'

export function AdminHomePage() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const [inviteModalOpen, setInviteModalOpen] = useState(false)
  const tab = (searchParams.get('tab') as Tab | null) ?? 'overview'
  const canInvite = canManageInvites(user)

  const tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: 'Overview' },
    { key: 'users', label: 'Users' },
    { key: 'global-settings', label: 'Global Settings' },
    { key: 'audit-log', label: 'Audit Log' },
    ...(canInvite ? [{ key: 'invites' as const, label: 'Invites' }] : [])
  ]

  function setTab(next: Tab) {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev)
      if (next === 'overview') params.delete('tab')
      else params.set('tab', next)
      return params
    })
  }

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-8">
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold text-forest-900">Admin</h1>
        {canInvite && (
          <Button variant="secondary" className="ml-auto" onClick={() => setInviteModalOpen(true)}>
            Invite
          </Button>
        )}
      </div>

      <div className="mb-6 flex gap-1 border-b border-forest-200">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`px-4 py-2 text-sm font-medium transition-colors ${
              tab === t.key ? 'border-b-2 border-forest-700 text-forest-900' : 'text-forest-500 hover:text-forest-700'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'overview' && <AdminOverviewTab />}
      {tab === 'users' && <AdminUsersTab />}
      {tab === 'global-settings' && <AdminGlobalSettingsTab />}
      {tab === 'audit-log' && <AdminAuditLogTab />}
      {tab === 'invites' && canInvite && <AdminInvitesTab />}

      {inviteModalOpen && <InviteModal onClose={() => setInviteModalOpen(false)} />}
    </div>
  )
}

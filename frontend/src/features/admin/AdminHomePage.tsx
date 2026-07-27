import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { AdminOverviewTab } from './AdminOverviewTab'
import { AdminGlobalSettingsTab } from './AdminGlobalSettingsTab'
import { AdminAuditLogTab } from './AdminAuditLogTab'

type Tab = 'overview' | 'global-settings' | 'audit-log'

const TABS: { key: Tab; label: string }[] = [
  { key: 'overview', label: 'Overview' },
  { key: 'global-settings', label: 'Global Settings' },
  { key: 'audit-log', label: 'Audit Log' }
]

export function AdminHomePage() {
  const [tab, setTab] = useState<Tab>('overview')

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-8">
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold text-forest-900">Admin</h1>
        <Link to="/admin/users" className="ml-auto">
          <Button variant="secondary">Manage Users</Button>
        </Link>
      </div>

      <div className="mb-6 flex gap-1 border-b border-forest-200">
        {TABS.map((t) => (
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
      {tab === 'global-settings' && <AdminGlobalSettingsTab />}
      {tab === 'audit-log' && <AdminAuditLogTab />}
    </div>
  )
}

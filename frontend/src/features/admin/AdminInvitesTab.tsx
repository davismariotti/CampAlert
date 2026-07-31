import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { adminDeactivateInvite, adminListInvites } from '../../api/generated/sdk.gen'
import type { AdminInviteStatus, AdminInviteSummary } from '../../api/generated/types.gen'
import { useApiMutation } from '../../hooks/useApiMutation'
import { Button } from '../../components/ui/Button'
import { Toggle } from '../../components/ui/Toggle'

const PAGE_SIZE = 20

const STATUS_STYLES: Record<AdminInviteStatus, string> = {
  ACTIVE: 'bg-green-100 text-green-800',
  EXPIRED: 'bg-stone-200 text-stone-600',
  DEACTIVATED: 'bg-red-100 text-red-700',
  REDEEMED: 'bg-blue-100 text-blue-800'
}

function formatDateTime(dateStr: string) {
  return new Date(dateStr).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit'
  })
}

function DeactivateButton({ invite }: { invite: AdminInviteSummary }) {
  const queryClient = useQueryClient()
  const mutation = useApiMutation({
    mutationFn: async () => {
      const result = await adminDeactivateInvite({ path: { id: invite.id } })
      if (result.error) throw result
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-invites'] }),
    errorMessage: 'Failed to deactivate invite. Please try again.'
  })

  if (invite.status !== 'ACTIVE') return null

  return (
    <button
      type="button"
      disabled={mutation.isPending}
      onClick={() => mutation.mutate()}
      className="text-xs font-medium text-red-600 hover:underline disabled:opacity-50"
    >
      Deactivate
    </button>
  )
}

export function AdminInvitesTab() {
  const [page, setPage] = useState(0)
  const [includeInactive, setIncludeInactive] = useState(false)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-invites', page, includeInactive],
    queryFn: async () => {
      const result = await adminListInvites({ query: { page, pageSize: PAGE_SIZE, includeInactive } })
      if (result.error) throw result
      return result.data!
    },
    placeholderData: (prev) => prev
  })

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  if (isLoading) return <p className="text-sm text-forest-500">Loading…</p>
  if (isError) return <p className="text-sm text-red-600">Failed to load invites.</p>

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <Toggle
          checked={includeInactive}
          onChange={(checked) => {
            setIncludeInactive(checked)
            setPage(0)
          }}
          label="Show expired, deactivated, and used invites"
        />
      </div>

      <div className="overflow-x-auto rounded-xl border border-forest-200">
        <table className="w-full text-left text-sm">
          <thead className="bg-forest-50 text-xs font-medium uppercase text-forest-500">
            <tr>
              <th className="px-4 py-2">Recipient</th>
              <th className="px-4 py-2">Status</th>
              <th className="px-4 py-2">Redeemed</th>
              <th className="px-4 py-2">Created by</th>
              <th className="px-4 py-2">Expires</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-forest-100">
            {(data?.items.length ?? 0) === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-forest-500" colSpan={6}>
                  No invites.
                </td>
              </tr>
            )}
            {data?.items.map((invite) => (
              <tr key={invite.id}>
                <td className="px-4 py-2 text-forest-900">
                  {invite.email ?? <span className="text-forest-400">Public link</span>}
                </td>
                <td className="px-4 py-2">
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[invite.status]}`}>
                    {invite.status}
                  </span>
                </td>
                <td className="whitespace-nowrap px-4 py-2 text-forest-600">
                  {invite.usedCount} of {invite.maxUses} redeemed
                </td>
                <td className="px-4 py-2 text-forest-600">{invite.createdByEmail}</td>
                <td className="whitespace-nowrap px-4 py-2 text-forest-600">{formatDateTime(invite.expiresAt)}</td>
                <td className="px-4 py-2 text-right">
                  <DeactivateButton invite={invite} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm">
          <Button variant="ghost" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
            Previous
          </Button>
          <span className="text-forest-500">
            Page {page + 1} of {totalPages}
          </span>
          <Button variant="ghost" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
            Next
          </Button>
        </div>
      )}
    </div>
  )
}

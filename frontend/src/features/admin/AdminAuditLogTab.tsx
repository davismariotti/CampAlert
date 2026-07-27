import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { adminListAuditLog } from '../../api/generated/sdk.gen'

const PAGE_SIZE = 20

function formatDateTime(dateStr: string) {
  return new Date(dateStr).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit'
  })
}

export function AdminAuditLogTab() {
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-audit-log', page],
    queryFn: async () => {
      const result = await adminListAuditLog({ query: { page, pageSize: PAGE_SIZE } })
      if (result.error) throw result
      return result.data!
    },
    placeholderData: (prev) => prev
  })

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  if (isLoading) return <p className="text-sm text-forest-500">Loading…</p>
  if (isError) return <p className="text-sm text-red-600">Failed to load audit log.</p>

  return (
    <div>
      <div className="overflow-x-auto rounded-xl border border-forest-200">
        <table className="w-full text-left text-sm">
          <thead className="bg-forest-50 text-xs font-medium uppercase text-forest-500">
            <tr>
              <th className="px-4 py-2">When</th>
              <th className="px-4 py-2">Admin</th>
              <th className="px-4 py-2">Action</th>
              <th className="px-4 py-2">Target</th>
              <th className="px-4 py-2">Detail</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-forest-100">
            {(data?.items.length ?? 0) === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-forest-500" colSpan={5}>
                  No audit log entries.
                </td>
              </tr>
            )}
            {data?.items.map((entry) => (
              <tr key={entry.id}>
                <td className="whitespace-nowrap px-4 py-2 text-forest-600">{formatDateTime(entry.createdAt)}</td>
                <td className="px-4 py-2 text-forest-900">{entry.actorEmail ?? `#${entry.actorUserId}`}</td>
                <td className="px-4 py-2 font-medium text-forest-900">{entry.action}</td>
                <td className="px-4 py-2 text-forest-600">
                  {entry.targetUserId ? (entry.targetEmail ?? `#${entry.targetUserId}`) : '—'}
                </td>
                <td className="px-4 py-2 text-forest-500">{entry.detail ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-lg px-3 py-1.5 font-medium text-forest-600 hover:bg-forest-100 disabled:opacity-40"
          >
            Previous
          </button>
          <span className="text-forest-500">
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-lg px-3 py-1.5 font-medium text-forest-600 hover:bg-forest-100 disabled:opacity-40"
          >
            Next
          </button>
        </div>
      )}
    </div>
  )
}

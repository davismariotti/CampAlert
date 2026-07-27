import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { adminListUserSearchRequests, adminListUserPermitSearchRequests } from '../../api/generated/sdk.gen'
import { RequestCard } from '../requests/RequestCard'

type Filter = 'active' | 'completed' | 'deleted'

const TABS: { key: Filter; label: string }[] = [
  { key: 'active', label: 'Active' },
  { key: 'completed', label: 'Completed' },
  { key: 'deleted', label: 'Deleted' }
]

export function AdminUserRequestsSection({ userId }: { userId: number }) {
  const [filter, setFilter] = useState<Filter>('active')
  const completed = filter === 'completed' ? true : filter === 'active' ? false : undefined
  const deleted = filter === 'deleted'

  const { data: campgroundRequests, isLoading: isLoadingCampgrounds } = useQuery({
    queryKey: ['admin-user-search-requests', userId, filter],
    queryFn: async () => {
      const result = await adminListUserSearchRequests({ path: { id: userId }, query: { completed, deleted } })
      if (result.error) throw result
      return result.data ?? []
    }
  })

  const { data: permitRequests, isLoading: isLoadingPermits } = useQuery({
    queryKey: ['admin-user-permit-search-requests', userId, filter],
    queryFn: async () => {
      const result = await adminListUserPermitSearchRequests({ path: { id: userId }, query: { completed, deleted } })
      if (result.error) throw result
      return result.data ?? []
    }
  })

  const isLoading = isLoadingCampgrounds || isLoadingPermits
  const all = [...(campgroundRequests ?? []), ...(permitRequests ?? [])]

  return (
    <div>
      <div className="mb-4 flex gap-1 border-b border-forest-200">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setFilter(tab.key)}
            className={`px-4 py-2 text-sm font-medium transition-colors ${
              filter === tab.key
                ? 'border-b-2 border-forest-700 text-forest-900'
                : 'text-forest-500 hover:text-forest-700'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {isLoading && <p className="text-sm text-forest-500">Loading…</p>}

      {!isLoading && all.length === 0 && <p className="text-sm text-forest-500">No requests here.</p>}

      {!isLoading && all.length > 0 && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {(campgroundRequests ?? []).map((request) => (
            <RequestCard key={`campground-${request.id}`} request={request} userId={userId} readOnly={deleted} />
          ))}
          {(permitRequests ?? []).map((request) => (
            <RequestCard key={`permit-${request.id}`} request={request} userId={userId} readOnly={deleted} />
          ))}
        </div>
      )}
    </div>
  )
}

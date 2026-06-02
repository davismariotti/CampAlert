import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listSearchRequests } from '../../api/generated/sdk.gen'
import { RequestCard } from './RequestCard'

type Filter = 'all' | 'watching' | 'done'

function SkeletonCard() {
  return (
    <div className="animate-pulse rounded-2xl bg-white p-6 shadow-sm">
      <div className="mb-3 h-4 w-2/3 rounded bg-forest-100" />
      <div className="mb-2 h-3 w-1/2 rounded bg-forest-100" />
      <div className="h-3 w-1/3 rounded bg-forest-100" />
    </div>
  )
}

export function RequestsPage() {
  const [filter, setFilter] = useState<Filter>('all')

  const completed = filter === 'all' ? undefined : filter === 'done'

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['search-requests', filter],
    queryFn: () =>
      listSearchRequests({ query: { completed } }).then((r) => r.data ?? []),
  })

  const all = data ?? []
  const watchingCount = all.filter((r) => !r.completed).length
  const doneCount = all.filter((r) => r.completed).length

  const tabs: { key: Filter; label: string }[] = [
    { key: 'all', label: 'All' },
    { key: 'watching', label: 'Watching' },
    { key: 'done', label: 'Done' },
  ]

  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-8">
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold text-forest-900">My Alerts</h1>
        <span className="rounded-full bg-forest-100 px-2.5 py-0.5 text-sm font-medium text-forest-600">
          {watchingCount} watching
        </span>
        <span className="rounded-full bg-neutral-100 px-2.5 py-0.5 text-sm font-medium text-neutral-500">
          {doneCount} done
        </span>
        <Link
          to="/"
          className="ml-auto rounded-xl border border-forest-600 px-4 py-1.5 text-sm font-medium text-forest-600 hover:bg-forest-100"
        >
          + New Alert
        </Link>
      </div>

      <div className="mb-6 flex gap-1 border-b border-forest-200">
        {tabs.map((tab) => (
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

      {isLoading && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <SkeletonCard />
          <SkeletonCard />
          <SkeletonCard />
        </div>
      )}

      {isError && (
        <div className="text-center">
          <p className="text-forest-600">Failed to load alerts.</p>
          <button
            type="button"
            onClick={() => refetch()}
            className="mt-2 text-sm font-medium text-forest-800 hover:underline"
          >
            Retry
          </button>
        </div>
      )}

      {!isLoading && !isError && all.length === 0 && (
        <div className="text-center">
          <p className="text-forest-600">No alerts here yet.</p>
          <Link to="/" className="mt-2 block text-sm font-medium text-forest-800 hover:underline">
            Create your first alert
          </Link>
        </div>
      )}

      {!isLoading && !isError && all.length > 0 && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {all.map((request) => (
            <RequestCard key={request.id} request={request} />
          ))}
        </div>
      )}
    </div>
  )
}

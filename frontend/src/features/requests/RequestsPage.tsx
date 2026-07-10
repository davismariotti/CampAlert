import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listSearchRequests, listPermitSearchRequests, listPhoneNumbers } from '../../api/generated/sdk.gen'
import { RequestCard } from './RequestCard'
import { AddAlertModal } from './AddAlertModal'

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
  const [showAddModal, setShowAddModal] = useState(false)

  const completed = filter === 'all' ? undefined : filter === 'done'

  const {
    data: campgroundRequests,
    isLoading: isLoadingCampgrounds,
    isError: isErrorCampgrounds,
    refetch: refetchCampgrounds
  } = useQuery({
    queryKey: ['search-requests', filter],
    queryFn: () => listSearchRequests({ query: { completed } }).then((r) => r.data ?? [])
  })

  const {
    data: permitRequests,
    isLoading: isLoadingPermits,
    isError: isErrorPermits,
    refetch: refetchPermits
  } = useQuery({
    queryKey: ['permit-search-requests', filter],
    queryFn: () => listPermitSearchRequests({ query: { completed } }).then((r) => r.data ?? [])
  })

  const { data: phones } = useQuery({
    queryKey: ['phone-numbers'],
    queryFn: () => listPhoneNumbers().then((r) => r.data ?? [])
  })

  const hasVerifiedPhone = (phones ?? []).some((p) => p.status === 'VERIFIED')

  const isLoading = isLoadingCampgrounds || isLoadingPermits
  const isError = isErrorCampgrounds || isErrorPermits

  const all = [...(campgroundRequests ?? []), ...(permitRequests ?? [])]
  const watchingCount = all.filter((r) => !r.completed).length
  const doneCount = all.filter((r) => r.completed).length

  function refetch() {
    refetchCampgrounds()
    refetchPermits()
  }

  const tabs: { key: Filter; label: string }[] = [
    { key: 'all', label: 'All' },
    { key: 'watching', label: 'Watching' },
    { key: 'done', label: 'Done' }
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
        <button
          type="button"
          onClick={() => setShowAddModal(true)}
          className="ml-auto rounded-xl border border-forest-600 px-4 py-1.5 text-sm font-medium text-forest-600 hover:bg-forest-100"
        >
          + New Alert
        </button>
      </div>

      {phones !== undefined && !hasVerifiedPhone && (
        <div className="mb-5 flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
          <span className="mt-0.5 text-amber-500">⚠</span>
          <p className="text-sm text-amber-800">
            You don't have a verified phone number. Alerts won't fire and new alerts can't be created.{' '}
            <Link to="/phone-numbers" className="font-medium underline hover:text-amber-900">
              Add a phone number
            </Link>
            .
          </p>
        </div>
      )}

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
          <button
            type="button"
            onClick={() => setShowAddModal(true)}
            className="mt-2 text-sm font-medium text-forest-800 hover:underline"
          >
            Create your first alert
          </button>
        </div>
      )}

      {!isLoading && !isError && all.length > 0 && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {(campgroundRequests ?? []).map((request) => (
            <RequestCard key={`campground-${request.id}`} request={request} />
          ))}
          {(permitRequests ?? []).map((request) => (
            <RequestCard key={`permit-${request.id}`} request={request} />
          ))}
        </div>
      )}

      {showAddModal && <AddAlertModal onClose={() => setShowAddModal(false)} />}
    </div>
  )
}

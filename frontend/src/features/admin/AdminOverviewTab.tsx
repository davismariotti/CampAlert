import { useQuery } from '@tanstack/react-query'
import { adminGetStats } from '../../api/generated/sdk.gen'
import { providerName } from '../../utils/providers'

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl bg-forest-50 p-4">
      <dt className="text-xs font-medium text-forest-500">{label}</dt>
      <dd className="mt-1 text-2xl font-semibold text-forest-900">{value}</dd>
    </div>
  )
}

export function AdminOverviewTab() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['admin-stats'],
    queryFn: async () => {
      const result = await adminGetStats()
      if (result.error) throw result
      return result.data!
    }
  })

  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="h-20 animate-pulse rounded-xl bg-forest-100" />
        ))}
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="text-center">
        <p className="text-forest-600">Failed to load stats.</p>
        <button
          type="button"
          onClick={() => refetch()}
          className="mt-2 text-sm font-medium text-forest-800 hover:underline"
        >
          Retry
        </button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <StatCard label="Total users" value={data.totalUsers} />
        <StatCard label="Active in last 30 days" value={data.activeUsersLast30Days} />
        <StatCard
          label="Campground requests"
          value={data.requestCounts.reduce((sum, r) => sum + r.campgroundCount, 0)}
        />
        <StatCard label="Permit requests" value={data.requestCounts.reduce((sum, r) => sum + r.permitCount, 0)} />
      </dl>

      <div>
        <h2 className="mb-3 text-sm font-semibold text-forest-900">Requests by provider</h2>
        <div className="overflow-x-auto rounded-xl border border-forest-200">
          <table className="w-full text-left text-sm">
            <thead className="bg-forest-50 text-xs font-medium uppercase text-forest-500">
              <tr>
                <th className="px-4 py-2">Provider</th>
                <th className="px-4 py-2">Campground</th>
                <th className="px-4 py-2">Permit</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-forest-100">
              {data.requestCounts.map((row) => (
                <tr key={row.provider}>
                  <td className="px-4 py-2 font-medium text-forest-900">{providerName(row.provider)}</td>
                  <td className="px-4 py-2 text-forest-600">{row.campgroundCount}</td>
                  <td className="px-4 py-2 text-forest-600">{row.permitCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

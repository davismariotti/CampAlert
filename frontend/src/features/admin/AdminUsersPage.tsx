import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { adminListUsers } from '../../api/generated/sdk.gen'
import { useDebounce } from '../../hooks/useDebounce'
import { Input } from '../../components/ui/Input'

const PAGE_SIZE = 20

function formatDate(dateStr: string | null | undefined) {
  if (!dateStr) return 'Never'
  return new Date(dateStr).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

export function AdminUsersPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('query') ?? ''
  const page = Number(searchParams.get('page') ?? '0')
  const debouncedQuery = useDebounce(query, 300)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-users', debouncedQuery, page],
    queryFn: async () => {
      const result = await adminListUsers({ query: { query: debouncedQuery || undefined, page, pageSize: PAGE_SIZE } })
      if (result.error) throw result
      return result.data!
    },
    placeholderData: (prev) => prev
  })

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  function setQuery(next: string) {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev)
      if (next) params.set('query', next)
      else params.delete('query')
      params.delete('page')
      return params
    })
  }

  function setPage(next: number) {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev)
      if (next > 0) params.set('page', String(next))
      else params.delete('page')
      return params
    })
  }

  function goToUser(id: number) {
    const from = `/admin/users?${searchParams.toString()}`
    navigate(`/admin/users/${id}?from=${encodeURIComponent(from)}`)
  }

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-8">
      <h1 className="mb-6 text-2xl font-semibold text-forest-900">Users</h1>

      <div className="relative mb-4 max-w-sm">
        <Input placeholder="Search by email or phone" value={query} onChange={(e) => setQuery(e.target.value)} />
        {query !== '' && (
          <button
            type="button"
            onClick={() => setQuery('')}
            aria-label="Clear search"
            className="absolute right-2 top-1/2 -translate-y-1/2 text-forest-400 hover:text-forest-700"
          >
            ×
          </button>
        )}
      </div>

      {isError && <p className="mb-4 text-sm text-red-600">Failed to load users. Please refresh.</p>}

      <div className="overflow-x-auto rounded-xl border border-forest-200">
        <table className="w-full text-left text-sm">
          <thead className="bg-forest-50 text-xs font-medium uppercase text-forest-500">
            <tr>
              <th className="px-4 py-2">Email</th>
              <th className="px-4 py-2">Last login</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-forest-100">
            {isLoading &&
              [1, 2, 3].map((i) => (
                <tr key={i}>
                  <td className="px-4 py-3" colSpan={2}>
                    <div className="h-4 w-1/2 animate-pulse rounded bg-forest-100" />
                  </td>
                </tr>
              ))}
            {!isLoading && (data?.items.length ?? 0) === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-forest-500" colSpan={2}>
                  No users found.
                </td>
              </tr>
            )}
            {!isLoading &&
              data?.items.map((u) => (
                <tr key={u.id} onClick={() => goToUser(u.id)} className="cursor-pointer hover:bg-forest-50">
                  <td className="px-4 py-3 font-medium text-forest-900">{u.email}</td>
                  <td className="px-4 py-3 text-forest-600">{formatDate(u.lastLoginAt)}</td>
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
            onClick={() => setPage(Math.max(0, page - 1))}
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
            onClick={() => setPage(page + 1)}
            className="rounded-lg px-3 py-1.5 font-medium text-forest-600 hover:bg-forest-100 disabled:opacity-40"
          >
            Next
          </button>
        </div>
      )}
    </div>
  )
}

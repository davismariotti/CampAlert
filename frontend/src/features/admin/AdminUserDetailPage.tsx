import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { adminGetUser } from '../../api/generated/sdk.gen'
import { AdminUserProfileSection } from './AdminUserProfileSection'
import { AdminUserRequestsSection } from './AdminUserRequestsSection'
import { AdminUserConfigSection } from './AdminUserConfigSection'

export function AdminUserDetailPage() {
  const { id } = useParams<{ id: string }>()
  const userId = Number(id)
  const [searchParams] = useSearchParams()
  const from = searchParams.get('from')

  const {
    data: user,
    isLoading,
    isError
  } = useQuery({
    queryKey: ['admin-user', userId],
    queryFn: async () => {
      const result = await adminGetUser({ path: { id: userId } })
      if (result.error) throw result
      return result.data!
    }
  })

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-8">
      {from && (
        <Link to={from} className="mb-4 inline-block text-sm font-medium text-forest-600 hover:text-forest-800">
          ← Back to search results
        </Link>
      )}

      {isLoading && <p className="text-sm text-forest-500">Loading…</p>}
      {isError && <p className="text-sm text-red-600">Failed to load user. Please refresh.</p>}

      {user && (
        <>
          <h1 className="mb-6 text-2xl font-semibold text-forest-900">{user.email}</h1>

          <div className="flex flex-col gap-8">
            <AdminUserProfileSection user={user} />
            <div>
              <h2 className="mb-3 text-sm font-semibold text-forest-900">Search requests</h2>
              <AdminUserRequestsSection userId={userId} />
            </div>
            <AdminUserConfigSection user={user} userId={userId} />
          </div>
        </>
      )}
    </div>
  )
}

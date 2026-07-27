import { Link, useParams, useSearchParams } from 'react-router-dom'

export function AdminUserDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const from = searchParams.get('from')

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-8">
      {from && (
        <Link to={from} className="mb-4 inline-block text-sm font-medium text-forest-600 hover:text-forest-800">
          ← Back to search results
        </Link>
      )}
      <h1 className="mb-6 text-2xl font-semibold text-forest-900">User #{id}</h1>
      <p className="text-sm text-forest-500">Coming soon.</p>
    </div>
  )
}

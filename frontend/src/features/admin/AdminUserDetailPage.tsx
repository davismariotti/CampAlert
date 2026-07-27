import { useParams } from 'react-router-dom'

export function AdminUserDetailPage() {
  const { id } = useParams<{ id: string }>()
  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-8">
      <h1 className="mb-6 text-2xl font-semibold text-forest-900">User #{id}</h1>
      <p className="text-sm text-forest-500">Coming soon.</p>
    </div>
  )
}

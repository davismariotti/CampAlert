import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listPhoneNumbers } from '../api/generated/sdk.gen'
import { CampgroundSearch } from '../features/campground/CampgroundSearch'
import { RequestBuilder } from '../features/requests/RequestBuilder'
import type { CampgroundSearchResult } from '../api/generated/types.gen'

export function AppHomePage() {
  const [selected, setSelected] = useState<CampgroundSearchResult | null>(null)

  const { data: phones, isLoading } = useQuery({
    queryKey: ['phone-numbers'],
    queryFn: () => listPhoneNumbers().then((r) => r.data ?? [])
  })

  const hasVerifiedPhone = (phones ?? []).some((p) => p.status === 'VERIFIED')

  if (!isLoading && !hasVerifiedPhone) {
    return (
      <div className="mx-auto w-full max-w-lg px-4 py-16">
        <h1 className="mb-2 text-3xl font-bold tracking-tight text-forest-900">Get started</h1>
        <p className="mb-8 text-forest-600">Add a phone number to receive campsite alerts.</p>

        <div className="rounded-2xl bg-white p-8 shadow-sm">
          <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-forest-100 text-2xl">📱</div>
          <h2 className="mb-2 font-semibold text-forest-900">Verify your phone</h2>
          <p className="mb-6 text-sm text-forest-600">
            CampAlert sends alerts via SMS. Add and verify your phone number to receive campsite notifications.
          </p>
          <Link
            to="/phone-numbers"
            className="inline-flex items-center gap-2 rounded-xl bg-forest-800 px-5 py-2.5 text-sm font-medium text-white hover:bg-forest-700"
          >
            Add phone number →
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto w-full max-w-xl px-4 py-12">
      {!selected && (
        <>
          <h1 className="mb-2 text-3xl font-bold tracking-tight text-forest-900">New alert</h1>
          <p className="mb-6 text-forest-600">Search for a campground to start watching.</p>
        </>
      )}

      {!selected && <CampgroundSearch onSelect={(cg) => setSelected(cg)} />}
      {selected && <RequestBuilder campground={selected} onClear={() => setSelected(null)} />}
    </div>
  )
}

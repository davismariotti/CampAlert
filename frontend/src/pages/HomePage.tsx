import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listPhoneNumbers } from '../api/generated/sdk.gen'
import { CampgroundSearch } from '../features/campground/CampgroundSearch'
import { RequestBuilder } from '../features/requests/RequestBuilder'
import type { CampgroundSearchResult } from '../api/generated/types.gen'

export function HomePage() {
  const [selected, setSelected] = useState<CampgroundSearchResult | null>(null)

  const { data: phones, isLoading } = useQuery({
    queryKey: ['phone-numbers'],
    queryFn: () => listPhoneNumbers().then((r) => r.data ?? [])
  })

  const hasVerifiedPhone = (phones ?? []).some((p) => p.status === 'VERIFIED')

  return (
    <div className="mx-auto w-full max-w-xl px-4 py-12">
      <div className="mb-8 flex flex-col items-center gap-2 text-center">
        <img src="/logo.png" alt="CampAlert" className="h-20 w-20 rounded-2xl" />
        <h1 className="text-3xl font-semibold text-forest-900">CampAlert</h1>
        <p className="text-forest-600">Never miss a campsite opening.</p>
      </div>

      {!isLoading && !hasVerifiedPhone && (
        <div className="rounded-2xl bg-white p-8 shadow-sm text-center">
          <p className="mb-1 font-medium text-forest-900">Add a phone number to get started</p>
          <p className="mb-5 text-sm text-forest-500">
            CampAlert sends alerts via SMS. You need a verified phone number before creating alerts.
          </p>
          <Link
            to="/phone-numbers"
            className="inline-flex items-center justify-center rounded-xl bg-forest-800 px-5 py-2 text-sm font-medium text-white hover:bg-forest-700"
          >
            Set up phone number
          </Link>
        </div>
      )}

      {hasVerifiedPhone && !selected && <CampgroundSearch onSelect={(cg) => setSelected(cg)} />}

      {hasVerifiedPhone && selected && <RequestBuilder campground={selected} onClear={() => setSelected(null)} />}
    </div>
  )
}

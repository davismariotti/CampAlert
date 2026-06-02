import { useState, useRef } from 'react'
import { useCampgroundSearch } from './useCampgroundSearch'
import { Spinner } from '../../components/ui/Spinner'
import type { CampgroundSearchResult } from '../../api/generated/types.gen'

interface Props {
  onSelect: (campground: CampgroundSearchResult) => void
}

export function CampgroundSearch({ onSelect }: Props) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const { data, isFetching, isError } = useCampgroundSearch(query)

  const showDropdown = open && query.trim().length >= 3

  return (
    <div className="relative w-full">
      <div className="relative">
        <input
          ref={inputRef}
          type="text"
          className="w-full rounded-xl border border-forest-200 bg-white py-3 pl-4 pr-10 text-forest-900 placeholder:text-forest-300 focus:border-forest-500 focus:outline-none focus:ring-2 focus:ring-forest-500/30"
          placeholder="Search for a campground..."
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            setOpen(true)
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          autoComplete="off"
        />
        {isFetching && (
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-forest-400">
            <Spinner size="sm" />
          </span>
        )}
      </div>

      {showDropdown && (
        <div className="absolute z-10 mt-1 w-full rounded-2xl bg-white shadow-md">
          {isError && <p className="px-4 py-3 text-sm text-red-600">Failed to load results. Try again.</p>}
          {!isError && !isFetching && data?.length === 0 && (
            <p className="px-4 py-3 text-sm text-forest-400">No campgrounds found</p>
          )}
          {!isError &&
            (data ?? []).map((cg) => (
              <button
                key={cg.id}
                type="button"
                className="flex w-full flex-col px-4 py-3 text-left hover:bg-forest-100 first:rounded-t-2xl last:rounded-b-2xl"
                onMouseDown={() => {
                  onSelect(cg)
                  setQuery('')
                  setOpen(false)
                }}
              >
                <span className="text-sm font-medium text-forest-900">{cg.name}</span>
                <span className="text-xs text-forest-500">ID: {cg.id}</span>
              </button>
            ))}
        </div>
      )}
    </div>
  )
}

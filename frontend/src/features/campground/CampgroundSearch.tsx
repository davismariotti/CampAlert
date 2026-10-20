import { useState, useRef } from 'react'
import { useCampgroundSearch } from './useCampgroundSearch'
import { Spinner } from '../../components/ui/Spinner'
import type { CampgroundSearchResult, ProviderType } from '../../api/generated/types.gen'

interface Props {
  onSelect: (campground: CampgroundSearchResult) => void
  /** Scope results to one provider (e.g. the Global Entry tab); omitted searches campgrounds across providers. */
  provider?: ProviderType
  /** Minimum characters before searching (default 3). */
  minChars?: number
  placeholder?: string
  emptyMessage?: string
}

export function CampgroundSearch({
  onSelect,
  provider,
  minChars = 3,
  placeholder = 'Search for a campground...',
  emptyMessage = 'No campgrounds found'
}: Props) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const { data, isFetching, isError } = useCampgroundSearch(query, { provider, minChars })

  const showDropdown = open && query.trim().length >= minChars

  return (
    <div className="relative w-full">
      <div className="relative">
        <input
          ref={inputRef}
          type="text"
          className="w-full rounded-xl border border-forest-200 bg-white py-3.5 pl-4 pr-10 text-forest-900 placeholder:text-forest-300 focus:border-forest-500 focus:outline-none focus:ring-2 focus:ring-forest-500/30"
          placeholder={placeholder}
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
            <p className="px-4 py-3 text-sm text-forest-400">{emptyMessage}</p>
          )}
          {!isError &&
            (data ?? []).map((cg) => (
              <button
                key={`${cg.provider.type}-${cg.id}`}
                type="button"
                className="flex w-full flex-col px-4 py-3 text-left hover:bg-forest-100 first:rounded-t-2xl last:rounded-b-2xl"
                onMouseDown={() => {
                  onSelect(cg)
                  setQuery('')
                  setOpen(false)
                }}
              >
                <span className="flex items-center gap-2">
                  <span className="text-sm font-medium text-forest-900">{cg.name}</span>
                  <span className="rounded-full bg-neutral-100 px-2.5 py-0.5 text-xs font-medium text-neutral-500">
                    {cg.provider.name}
                  </span>
                </span>
                <span className="text-xs text-forest-500">ID: {cg.id}</span>
              </button>
            ))}
        </div>
      )}
    </div>
  )
}

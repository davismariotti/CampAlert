import { useEffect, useRef, useState } from 'react'
import { AsYouType, parsePhoneNumber, type CountryCode } from 'libphonenumber-js'

const COUNTRIES: { code: CountryCode; name: string; dial: string }[] = [
  { code: 'US', name: 'United States', dial: '+1' },
  { code: 'CA', name: 'Canada', dial: '+1' },
  { code: 'MX', name: 'Mexico', dial: '+52' },
  { code: 'GB', name: 'United Kingdom', dial: '+44' },
  { code: 'AU', name: 'Australia', dial: '+61' },
  { code: 'NZ', name: 'New Zealand', dial: '+64' },
  { code: 'DE', name: 'Germany', dial: '+49' },
  { code: 'FR', name: 'France', dial: '+33' },
  { code: 'IT', name: 'Italy', dial: '+39' },
  { code: 'ES', name: 'Spain', dial: '+34' },
  { code: 'NL', name: 'Netherlands', dial: '+31' },
  { code: 'SE', name: 'Sweden', dial: '+46' },
  { code: 'NO', name: 'Norway', dial: '+47' },
  { code: 'DK', name: 'Denmark', dial: '+45' },
  { code: 'CH', name: 'Switzerland', dial: '+41' },
  { code: 'AT', name: 'Austria', dial: '+43' },
  { code: 'BE', name: 'Belgium', dial: '+32' },
  { code: 'PT', name: 'Portugal', dial: '+351' },
  { code: 'PL', name: 'Poland', dial: '+48' },
  { code: 'JP', name: 'Japan', dial: '+81' },
  { code: 'KR', name: 'South Korea', dial: '+82' },
  { code: 'SG', name: 'Singapore', dial: '+65' },
  { code: 'HK', name: 'Hong Kong', dial: '+852' },
  { code: 'IN', name: 'India', dial: '+91' },
  { code: 'BR', name: 'Brazil', dial: '+55' },
  { code: 'AR', name: 'Argentina', dial: '+54' },
  { code: 'AE', name: 'UAE', dial: '+971' },
  { code: 'ZA', name: 'South Africa', dial: '+27' },
  { code: 'IL', name: 'Israel', dial: '+972' }
]

function flag(code: string) {
  return code.toUpperCase().replace(/./g, (c) => String.fromCodePoint(127397 + c.charCodeAt(0)))
}

interface Props {
  value: string
  onChange: (e164: string) => void
  disabled?: boolean
}

export function PhoneInput({ value, onChange, disabled }: Props) {
  const [country, setCountry] = useState<CountryCode>('US')
  const [display, setDisplay] = useState('')
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const dropdownRef = useRef<HTMLDivElement>(null)
  const [prevValue, setPrevValue] = useState(value)

  if (prevValue !== value) {
    setPrevValue(value)
    if (!value && display) setDisplay('')
  }

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false)
        setSearch('')
      }
    }
    if (open) document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [open])

  function handleInput(raw: string) {
    const formatter = new AsYouType(country)
    const formatted = formatter.input(raw)
    setDisplay(formatted)

    try {
      const parsed = parsePhoneNumber(raw, country)
      if (parsed.isValid()) {
        onChange(parsed.format('E.164'))
        return
      }
    } catch {
      // not yet valid — that's fine while typing
    }
    onChange('')
  }

  function handleCountryChange(code: CountryCode) {
    setCountry(code)
    setOpen(false)
    setSearch('')
    // Re-format the current input for the new country
    if (display) {
      const digits = display.replace(/\D/g, '')
      handleInput(digits)
    }
  }

  const selected = COUNTRIES.find((c) => c.code === country)!
  const filtered = search
    ? COUNTRIES.filter((c) => c.name.toLowerCase().includes(search.toLowerCase()) || c.dial.includes(search))
    : COUNTRIES

  return (
    <div className="relative flex w-full rounded-xl border border-forest-200 bg-white focus-within:border-forest-500 focus-within:ring-2 focus-within:ring-forest-500/30">
      {/* Country selector */}
      <div className="relative" ref={dropdownRef}>
        <button
          type="button"
          disabled={disabled}
          onClick={() => setOpen((o) => !o)}
          className="flex h-full items-center gap-1.5 rounded-l-xl border-r border-forest-200 bg-forest-50 px-3 text-sm text-forest-700 hover:bg-forest-100 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <span>{flag(selected.code)}</span>
          <span className="font-medium">{selected.dial}</span>
          <svg className="h-3 w-3 text-forest-400" viewBox="0 0 12 12" fill="currentColor">
            <path d="M6 8L1 3h10L6 8z" />
          </svg>
        </button>

        {open && (
          <div className="absolute left-0 top-full z-50 mt-1 w-64 overflow-hidden rounded-xl border border-forest-200 bg-white shadow-lg">
            <div className="border-b border-forest-100 p-2">
              <input
                autoFocus
                placeholder="Search country…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full rounded-lg border border-forest-200 px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-forest-500/30"
              />
            </div>
            <ul className="max-h-56 overflow-y-auto py-1">
              {filtered.map((c) => (
                <li key={c.code}>
                  <button
                    type="button"
                    onClick={() => handleCountryChange(c.code)}
                    className={`flex w-full items-center gap-2.5 px-3 py-2 text-sm hover:bg-forest-50 ${c.code === country ? 'bg-forest-50 font-medium text-forest-900' : 'text-forest-700'}`}
                  >
                    <span>{flag(c.code)}</span>
                    <span className="flex-1 text-left">{c.name}</span>
                    <span className="text-xs text-forest-400">{c.dial}</span>
                  </button>
                </li>
              ))}
              {filtered.length === 0 && <li className="px-3 py-2 text-sm text-forest-400">No results</li>}
            </ul>
          </div>
        )}
      </div>

      {/* Number input */}
      <input
        type="tel"
        placeholder="(555) 555-5555"
        value={display}
        disabled={disabled}
        onChange={(e) => handleInput(e.target.value)}
        className="min-w-0 flex-1 rounded-r-xl bg-transparent px-3 py-2 text-sm text-forest-900 placeholder:text-forest-300 focus:outline-none disabled:cursor-not-allowed disabled:opacity-50"
      />
    </div>
  )
}

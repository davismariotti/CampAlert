import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { parsePhoneNumber } from 'libphonenumber-js'
import confetti from 'canvas-confetti'
import { listPhoneNumbers, addPhoneNumber, verifyPhoneNumber, deletePhoneNumber } from '../../api/generated/sdk.gen'
import { CONFETTI_ENABLED } from '../../config/featureFlags'
import type { PhoneNumberResponse } from '../../api/generated/types.gen'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { PhoneInput } from '../../components/ui/PhoneInput'
import { useToast } from '../../components/ui/useToast'
import { useApiMutation } from '../../hooks/useApiMutation'
import type { AxiosError } from 'axios'

function formatPhone(e164: string): string {
  try {
    const parsed = parsePhoneNumber(e164)
    return parsed.country === 'US' || parsed.country === 'CA' ? parsed.formatNational() : parsed.formatInternational()
  } catch {
    return e164
  }
}

type StatusColor = { bg: string; text: string; label: string }

function statusStyle(status: PhoneNumberResponse['status']): StatusColor {
  switch (status) {
    case 'VERIFIED':
      return { bg: 'bg-green-100', text: 'text-green-700', label: 'Verified' }
    case 'PENDING_VERIFICATION':
      return { bg: 'bg-amber-100', text: 'text-amber-700', label: 'Pending' }
    case 'OPTED_OUT':
      return { bg: 'bg-neutral-100', text: 'text-neutral-500', label: 'Opted Out' }
  }
}

function PhoneStatusBadge({ status }: { status: PhoneNumberResponse['status'] }) {
  const s = statusStyle(status)
  return <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${s.bg} ${s.text}`}>{s.label}</span>
}

interface VerifyRowProps {
  phone: PhoneNumberResponse
}

function VerifyRow({ phone }: VerifyRowProps) {
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const verifyMutation = useApiMutation({
    mutationFn: async () => {
      const result = await verifyPhoneNumber({ path: { id: phone.id }, body: { code } })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: () => {
      setCode('')
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['phone-numbers'] })
      if (CONFETTI_ENABLED) confetti({ particleCount: 120, spread: 70, origin: { y: 0.6 } })
    },
    onError: (err: AxiosError<{ code?: string; message?: string }>) => {
      const apiCode = err.response?.data?.code
      if (apiCode === 'INVALID_OTP') {
        setError('Incorrect code. Please try again.')
      } else if (apiCode === 'OTP_EXPIRED') {
        setError('Code expired. Delete this number and re-add it to get a new code.')
      } else {
        setError('Verification failed. Please try again.')
      }
    }
  })

  return (
    <div className="mt-3 flex flex-col gap-1">
      <div className="flex gap-2">
        <Input
          placeholder="6-digit code"
          value={code}
          onChange={(e) => {
            setCode(e.target.value)
            setError(null)
          }}
          maxLength={6}
          className="max-w-40"
        />
        <Button
          variant="secondary"
          loading={verifyMutation.isPending}
          disabled={code.length < 4}
          onClick={() => verifyMutation.mutate()}
        >
          Verify
        </Button>
      </div>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  )
}

interface PhoneCardProps {
  phone: PhoneNumberResponse
}

function PhoneCard({ phone }: PhoneCardProps) {
  const [showConfirm, setShowConfirm] = useState(false)
  const queryClient = useQueryClient()

  const deleteMutation = useApiMutation({
    mutationFn: async () => {
      const result = await deletePhoneNumber({ path: { id: phone.id } })
      if (result.error) throw result
    },
    onSuccess: () => {
      setShowConfirm(false)
      queryClient.invalidateQueries({ queryKey: ['phone-numbers'] })
      queryClient.invalidateQueries({ queryKey: ['search-requests'] })
    },
    errorMessage: 'Failed to remove phone number. Please try again.'
  })

  return (
    <>
      <div className="rounded-2xl bg-white p-5 shadow-sm">
        <div className="flex items-start justify-between">
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center gap-2">
              <span className="font-medium text-forest-900">{formatPhone(phone.phone)}</span>
              <PhoneStatusBadge status={phone.status} />
            </div>
            {phone.status === 'OPTED_OUT' && (
              <p className="text-xs text-neutral-500">
                You opted out. Reply UNSTOP from this number to re-enable alerts automatically.
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={() => setShowConfirm(true)}
            className="rounded-lg p-1.5 text-forest-400 hover:bg-red-50 hover:text-red-600"
            aria-label="Delete"
          >
            🗑️
          </button>
        </div>

        {phone.status === 'PENDING_VERIFICATION' && (
          <>
            <p className="mt-2 text-xs text-forest-600">Check your messages for a verification code.</p>
            <VerifyRow phone={phone} />
          </>
        )}
      </div>

      {showConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-md">
            <h2 className="mb-2 font-semibold text-forest-900">Remove phone number?</h2>
            <p className="mb-6 text-sm text-forest-600">
              {phone.phone} will be removed. Any active alerts may be paused if you have no remaining verified numbers.
            </p>
            <div className="flex justify-end gap-3">
              <Button variant="secondary" onClick={() => setShowConfirm(false)}>
                Cancel
              </Button>
              <Button
                className="bg-red-600 hover:bg-red-700"
                loading={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate()}
              >
                Remove
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

interface UnstopModalProps {
  phone: string
  onClose: () => void
}

function UnstopModal({ phone, onClose }: UnstopModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-md">
        <h2 className="mb-2 font-semibold text-forest-900">Carrier opt-in required</h2>
        <p className="mb-4 text-sm text-forest-600">
          <span className="font-medium">{phone}</span> was previously opted out. Before you can receive messages, text{' '}
          <span className="font-medium">UNSTOP</span> from that phone to our number. Then complete verification above.
        </p>
        <p className="mb-4 text-xs text-forest-400">This is a carrier-level restriction — we cannot bypass it.</p>
        <div className="flex justify-end">
          <Button onClick={onClose}>Got it</Button>
        </div>
      </div>
    </div>
  )
}

function AddPhoneForm() {
  const [e164, setE164] = useState('')
  const [consent, setConsent] = useState(false)
  const [inputError, setInputError] = useState<string | null>(null)
  const [showUnstop, setShowUnstop] = useState(false)
  const [unstopPhone, setUnstopPhone] = useState('')
  const queryClient = useQueryClient()
  const { showToast } = useToast()

  const addMutation = useApiMutation({
    mutationFn: async () => {
      const result = await addPhoneNumber({ body: { phone: e164, smsConsent: true } })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: (data) => {
      setE164('')
      setConsent(false)
      setInputError(null)
      queryClient.invalidateQueries({ queryKey: ['phone-numbers'] })
      if (data.requiresCarrierOptIn) {
        setUnstopPhone(data.phone)
        setShowUnstop(true)
      }
    },
    onError: (err: AxiosError<{ code?: string; message?: string }>) => {
      const apiCode = err.response?.data?.code
      if (apiCode === 'PHONE_ALREADY_REGISTERED') {
        setInputError('This number is already registered on an account.')
      } else if (err.response?.status === 400) {
        setInputError('Enter a valid phone number.')
      } else {
        showToast('Could not send verification code. Please try again.')
      }
    }
  })

  const canSubmit = e164 !== '' && consent

  return (
    <>
      <div className="rounded-2xl bg-white p-6 shadow-sm">
        <h2 className="mb-4 font-semibold text-forest-900">Add a phone number</h2>
        <div className="flex flex-col gap-4">
          <div>
            <PhoneInput
              value={e164}
              onChange={(val) => {
                setE164(val)
                setInputError(null)
              }}
              disabled={addMutation.isPending}
            />
            {inputError && <p className="mt-1 text-xs text-red-600">{inputError}</p>}
          </div>

          <label className="flex cursor-pointer items-start gap-3">
            <input
              type="checkbox"
              className="mt-0.5 h-4 w-4 accent-forest-700"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
            />
            <span className="text-sm text-forest-600">
              I agree to receive SMS notifications from CampAlert. Message and data rates may apply.{' '}
              <a href="/terms#sms-program" className="underline hover:text-forest-900">
                Terms
              </a>{' '}
              &amp;{' '}
              <a href="/privacy#phone-data" className="underline hover:text-forest-900">
                Privacy
              </a>
              .
            </span>
          </label>

          <Button loading={addMutation.isPending} disabled={!canSubmit} onClick={() => addMutation.mutate()}>
            Send verification code
          </Button>
        </div>
      </div>

      {showUnstop && <UnstopModal phone={unstopPhone} onClose={() => setShowUnstop(false)} />}
    </>
  )
}

export function PhoneNumbersPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['phone-numbers'],
    queryFn: () => listPhoneNumbers().then((r) => r.data ?? [])
  })

  const phones = data ?? []

  return (
    <div className="mx-auto w-full max-w-xl px-4 py-8">
      <h1 className="mb-6 text-2xl font-semibold text-forest-900">Phone Numbers</h1>

      {isLoading && (
        <div className="space-y-3">
          {[1, 2].map((i) => (
            <div key={i} className="animate-pulse rounded-2xl bg-white p-5 shadow-sm">
              <div className="h-4 w-1/2 rounded bg-forest-100" />
            </div>
          ))}
        </div>
      )}

      {isError && <p className="mb-4 text-sm text-red-600">Failed to load phone numbers. Please refresh.</p>}

      {!isLoading && phones.length > 0 && (
        <div className="mb-6 flex flex-col gap-3">
          {phones.map((p) => (
            <PhoneCard key={p.id} phone={p} />
          ))}
        </div>
      )}

      {!isLoading && phones.length === 0 && !isError && (
        <p className="mb-6 text-sm text-forest-500">No phone numbers yet. Add one below to enable alerts.</p>
      )}

      <AddPhoneForm />
    </div>
  )
}

import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { adminCreateEmailInvites, adminCreateInviteLink } from '../../api/generated/sdk.gen'
import type { AdminEmailInviteResult } from '../../api/generated/types.gen'
import { useApiMutation } from '../../hooks/useApiMutation'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'

interface Props {
  onClose: () => void
}

type InviteType = 'email' | 'link'

function parseEmails(raw: string): string[] {
  return raw
    .split(',')
    .map((e) => e.trim())
    .filter((e) => e.length > 0)
}

export function InviteModal({ onClose }: Props) {
  const [inviteType, setInviteType] = useState<InviteType>('email')
  const [emailsInput, setEmailsInput] = useState('')
  const [expiresInDays, setExpiresInDays] = useState('')
  const [maxUses, setMaxUses] = useState('1')
  const [emailResults, setEmailResults] = useState<AdminEmailInviteResult[] | null>(null)
  const [linkUrl, setLinkUrl] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const queryClient = useQueryClient()

  const emails = parseEmails(emailsInput)

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['admin-invites'] })
  }

  const sendEmailsMutation = useApiMutation({
    mutationFn: async () => {
      const result = await adminCreateEmailInvites({
        body: {
          emails,
          expiresInDays: expiresInDays.trim() === '' ? undefined : Number(expiresInDays)
        }
      })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: (data) => {
      setEmailResults(data.results)
      invalidate()
    },
    errorMessage: 'Failed to send invites. Please try again.'
  })

  const createLinkMutation = useApiMutation({
    mutationFn: async () => {
      const result = await adminCreateInviteLink({
        body: {
          maxUses: Number(maxUses),
          expiresInDays: expiresInDays.trim() === '' ? undefined : Number(expiresInDays)
        }
      })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: (data) => {
      setLinkUrl(data.url)
      invalidate()
    },
    errorMessage: 'Failed to generate invite link. Please try again.'
  })

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/50 px-4 py-8">
      <div className="w-full max-w-lg rounded-2xl bg-stone-50 p-6 shadow-xl">
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-xl font-semibold text-forest-900">Invite</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-forest-400 hover:bg-forest-100 hover:text-forest-700"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        {!emailResults && !linkUrl && (
          <div className="mb-4 flex gap-1 rounded-xl bg-forest-100 p-1">
            <button
              type="button"
              onClick={() => setInviteType('email')}
              className={`flex-1 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                inviteType === 'email' ? 'bg-white text-forest-900 shadow-sm' : 'text-forest-500 hover:text-forest-700'
              }`}
            >
              Email invites
            </button>
            <button
              type="button"
              onClick={() => setInviteType('link')}
              className={`flex-1 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                inviteType === 'link' ? 'bg-white text-forest-900 shadow-sm' : 'text-forest-500 hover:text-forest-700'
              }`}
            >
              Public link
            </button>
          </div>
        )}

        {inviteType === 'email' && !emailResults && (
          <div className="flex flex-col gap-4">
            <label className="flex flex-col gap-1 text-sm font-medium text-forest-700">
              Email addresses
              <textarea
                value={emailsInput}
                onChange={(e) => setEmailsInput(e.target.value)}
                placeholder="alice@example.com, bob@example.com"
                rows={3}
                className="w-full rounded-xl border border-forest-200 bg-white px-3 py-2 text-sm text-forest-900 placeholder:text-forest-300 focus:border-forest-500 focus:outline-none focus:ring-2 focus:ring-forest-500/30"
              />
              <span className="text-xs font-normal text-forest-500">
                Comma-separated. Each address gets its own invite link.
              </span>
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium text-forest-700">
              Expires in (days)
              <Input
                type="number"
                min={1}
                placeholder="7"
                value={expiresInDays}
                onChange={(e) => setExpiresInDays(e.target.value)}
                className="max-w-24"
              />
            </label>
            <Button
              loading={sendEmailsMutation.isPending}
              disabled={emails.length === 0}
              onClick={() => sendEmailsMutation.mutate()}
            >
              Send {emails.length > 0 ? `${emails.length} invite${emails.length === 1 ? '' : 's'}` : 'invites'}
            </Button>
          </div>
        )}

        {inviteType === 'email' && emailResults && (
          <div className="flex flex-col gap-3">
            <ul className="flex flex-col gap-2">
              {emailResults.map((result) => (
                <li
                  key={result.email}
                  className="flex items-center justify-between rounded-lg bg-white px-3 py-2 text-sm"
                >
                  <span className="text-forest-900">{result.email}</span>
                  {result.created ? (
                    <span className="font-medium text-green-700">Sent</span>
                  ) : (
                    <span className="font-medium text-amber-700">{result.reason ?? 'Not sent'}</span>
                  )}
                </li>
              ))}
            </ul>
            <Button variant="secondary" onClick={onClose}>
              Done
            </Button>
          </div>
        )}

        {inviteType === 'link' && !linkUrl && (
          <div className="flex flex-col gap-4">
            <label className="flex flex-col gap-1 text-sm font-medium text-forest-700">
              Number of uses
              <Input
                type="number"
                min={1}
                value={maxUses}
                onChange={(e) => setMaxUses(e.target.value)}
                className="max-w-24"
              />
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium text-forest-700">
              Expires in (days)
              <Input
                type="number"
                min={1}
                placeholder="7"
                value={expiresInDays}
                onChange={(e) => setExpiresInDays(e.target.value)}
                className="max-w-24"
              />
            </label>
            <Button
              loading={createLinkMutation.isPending}
              disabled={maxUses.trim() === '' || Number(maxUses) < 1}
              onClick={() => createLinkMutation.mutate()}
            >
              Generate link
            </Button>
          </div>
        )}

        {inviteType === 'link' && linkUrl && (
          <div className="flex flex-col gap-3">
            <p className="text-sm text-forest-600">
              Copy this link now — it can't be shown again. Share it with anyone you want to invite.
            </p>
            <div className="flex items-center gap-2 rounded-lg bg-white px-3 py-2">
              <input readOnly value={linkUrl} className="flex-1 truncate bg-transparent text-sm text-forest-900" />
              <button
                type="button"
                onClick={() => {
                  navigator.clipboard.writeText(linkUrl)
                  setCopied(true)
                }}
                className="shrink-0 rounded-lg px-2 py-1 text-xs font-medium text-forest-700 hover:bg-forest-100"
              >
                {copied ? 'Copied!' : 'Copy'}
              </button>
            </div>
            <Button variant="secondary" onClick={onClose}>
              Done
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}

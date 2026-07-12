import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ChangePasswordForm } from '../features/account/ChangePasswordForm'
import * as sdk from '../api/generated/sdk.gen'

async function fillForm(current: string, next: string, confirm = next) {
  await userEvent.type(screen.getByLabelText('Current password'), current)
  await userEvent.type(screen.getByLabelText('New password'), next)
  await userEvent.type(screen.getByLabelText('Confirm new password'), confirm)
}

describe('ChangePasswordForm', () => {
  afterEach(() => vi.restoreAllMocks())

  it('blocks submission when the new password matches the current password', async () => {
    const changeSpy = vi.spyOn(sdk, 'changePassword')
    render(
      <MemoryRouter>
        <ChangePasswordForm />
      </MemoryRouter>
    )
    await fillForm('samepassword1', 'samepassword1')
    await userEvent.click(screen.getByRole('button', { name: /update password/i }))

    expect(screen.getByText('New password must be different from current password.')).toBeInTheDocument()
    expect(changeSpy).not.toHaveBeenCalled()
  })

  it('submits when the new password differs from the current password', async () => {
    const changeSpy = vi.spyOn(sdk, 'changePassword').mockResolvedValueOnce({
      data: undefined,
      error: undefined
    } as Awaited<ReturnType<typeof sdk.changePassword>>)
    render(
      <MemoryRouter>
        <ChangePasswordForm />
      </MemoryRouter>
    )
    await fillForm('oldpassword1', 'newpassword1')
    await userEvent.click(screen.getByRole('button', { name: /update password/i }))

    await waitFor(() =>
      expect(changeSpy).toHaveBeenCalledWith({
        body: { currentPassword: 'oldpassword1', newPassword: 'newpassword1' }
      })
    )
    expect(screen.getByText('Password updated successfully.')).toBeInTheDocument()
  })
})

import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { AuthProvider, useAuth } from '../features/auth/AuthContext'
import { AUTH_STORAGE_KEY } from '../api/client'

function AuthDisplay() {
  const { user, login, logout } = useAuth()
  return (
    <div>
      <span data-testid="user">{user ? JSON.stringify(user) : 'null'}</span>
      <button onClick={() => login({ id: 1, email: 'a@b.com' })}>login</button>
      <button onClick={() => logout()}>logout</button>
    </div>
  )
}

describe('AuthContext', () => {
  beforeEach(() => localStorage.clear())

  it('starts with null user when localStorage is empty', () => {
    render(
      <AuthProvider>
        <AuthDisplay />
      </AuthProvider>
    )
    expect(screen.getByTestId('user').textContent).toBe('null')
  })

  it('login stores user in context and localStorage', () => {
    render(
      <AuthProvider>
        <AuthDisplay />
      </AuthProvider>
    )
    act(() => screen.getByText('login').click())
    expect(screen.getByTestId('user').textContent).toContain('a@b.com')
    expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY)!).email).toBe('a@b.com')
  })

  it('logout clears context and localStorage', () => {
    render(
      <AuthProvider>
        <AuthDisplay />
      </AuthProvider>
    )
    act(() => screen.getByText('login').click())
    act(() => screen.getByText('logout').click())
    expect(screen.getByTestId('user').textContent).toBe('null')
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('seeds from localStorage on mount', () => {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ id: 2, email: 'seed@test.com' }))
    render(
      <AuthProvider>
        <AuthDisplay />
      </AuthProvider>
    )
    expect(screen.getByTestId('user').textContent).toContain('seed@test.com')
  })
})

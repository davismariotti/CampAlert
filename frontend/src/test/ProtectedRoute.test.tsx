import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { ProtectedRoute } from '../components/ProtectedRoute'
import { AuthProvider } from '../features/auth/AuthContext'
import { AUTH_STORAGE_KEY } from '../api/client'

function Wrapper({ initialEntry }: { initialEntry: string }) {
  return (
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<div>home page</div>} />
          <Route
            path="/protected"
            element={
              <ProtectedRoute>
                <div>protected content</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('ProtectedRoute', () => {
  beforeEach(() => localStorage.clear())

  it('redirects to / when unauthenticated', () => {
    render(<Wrapper initialEntry="/protected" />)
    expect(screen.getByText('home page')).toBeInTheDocument()
    expect(screen.queryByText('protected content')).not.toBeInTheDocument()
  })

  it('renders children when authenticated', () => {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ id: 1, email: 'a@b.com' }))
    render(<Wrapper initialEntry="/protected" />)
    expect(screen.getByText('protected content')).toBeInTheDocument()
  })
})

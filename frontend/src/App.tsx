import { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './features/auth/AuthContext'
import { useAuth } from './features/auth/useAuth'
import { ProtectedRoute } from './components/ProtectedRoute'
import { Nav } from './components/Nav'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { HomePage } from './pages/HomePage'
import { RequestsPage } from './features/requests/RequestsPage'
import { PhoneNumbersPage } from './features/phones/PhoneNumbersPage'
import { TermsPage } from './pages/TermsPage'
import { PrivacyPage } from './pages/PrivacyPage'
import { getMe } from './api/generated/sdk.gen'
import { setNavigate, AUTH_STORAGE_KEY } from './api/client'
import { Spinner } from './components/ui/Spinner'

const queryClient = new QueryClient()

function AuthGate() {
  const { user, login, logout } = useAuth()
  const navigate = useNavigate()
  const [checking, setChecking] = useState(() => localStorage.getItem(AUTH_STORAGE_KEY) !== null)

  useEffect(() => {
    setNavigate((path) => navigate(path))
  }, [navigate])

  useEffect(() => {
    const stored = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!stored) return
    getMe()
      .then(({ data }) => {
        if (data) login(data)
        else {
          logout()
          navigate('/login')
        }
      })
      .catch(() => {
        logout()
        navigate('/login')
      })
      .finally(() => setChecking(false))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  if (checking) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Spinner size="lg" />
      </div>
    )
  }

  return (
    <>
      <Nav />
      <Routes>
        <Route path="/login" element={user ? <Navigate to="/" replace /> : <LoginPage />} />
        <Route path="/register" element={user ? <Navigate to="/" replace /> : <RegisterPage />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <HomePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/requests"
          element={
            <ProtectedRoute>
              <RequestsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/phone-numbers"
          element={
            <ProtectedRoute>
              <PhoneNumbersPage />
            </ProtectedRoute>
          }
        />
        <Route path="/terms" element={<TermsPage />} />
        <Route path="/privacy" element={<PrivacyPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <AuthGate />
        </AuthProvider>
      </QueryClientProvider>
    </BrowserRouter>
  )
}

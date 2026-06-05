import { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { AxiosError } from 'axios'
import { AuthProvider } from './features/auth/AuthContext'
import { useAuth } from './features/auth/useAuth'
import { ProtectedRoute } from './components/ProtectedRoute'
import { Nav } from './components/Nav'
import { RegisterPage } from './features/auth/RegisterPage'
import { AppHomePage } from './pages/HomePage'
import { LandingPage } from './pages/LandingPage'
import { RequestsPage } from './features/requests/RequestsPage'
import { PhoneNumbersPage } from './features/phones/PhoneNumbersPage'
import { AccountSettingsPage } from './features/account/AccountSettingsPage'
import { TermsPage } from './pages/TermsPage'
import { PrivacyPage } from './pages/PrivacyPage'
import { getMe } from './api/generated/sdk.gen'
import { setNavigate, setLogout, AUTH_STORAGE_KEY } from './api/client'
import { Spinner } from './components/ui/Spinner'
import { ToastProvider } from './components/ui/Toast'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if ((error as AxiosError)?.response?.status === 401) return false
        return failureCount < 3
      }
    }
  }
})

function AuthGate() {
  const { user, login, logout } = useAuth()
  const navigate = useNavigate()
  const [checking, setChecking] = useState(() => localStorage.getItem(AUTH_STORAGE_KEY) !== null)

  useEffect(() => {
    setNavigate((path) => navigate(path))
  }, [navigate])

  useEffect(() => {
    setLogout(logout)
  }, [logout])

  useEffect(() => {
    const stored = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!stored) return
    getMe()
      .then(({ data }) => {
        if (data) login(data)
        else {
          logout()
          navigate('/')
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
        <Route path="/register" element={user ? <Navigate to="/" replace /> : <RegisterPage />} />
        <Route path="/" element={user ? <AppHomePage /> : <LandingPage />} />
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
        <Route
          path="/account"
          element={
            <ProtectedRoute>
              <AccountSettingsPage />
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
        <ToastProvider>
          <AuthProvider>
            <AuthGate />
          </AuthProvider>
        </ToastProvider>
      </QueryClientProvider>
    </BrowserRouter>
  )
}

import { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Toaster } from 'sonner'
import { GoogleOAuthProvider } from '@react-oauth/google'
import { useAuthStore } from '@/infrastructure/store/auth.store'
import { useThemeStore } from '@/infrastructure/store/theme.store'
import { authRepository } from '@/infrastructure/http/AuthHttpRepository'
import { ClienteGuard } from '@/ui/guards/ClienteGuard'
import { AppLayout } from '@/ui/layouts/AppLayout'
import { LoginPage } from '@/ui/pages/login/LoginPage'
import { ForgotPasswordPage } from '@/ui/pages/forgot-password/ForgotPasswordPage'
import { ResetPasswordPage } from '@/ui/pages/reset-password/ResetPasswordPage'
import { HomePage } from '@/ui/pages/home/HomePage'
import { CheckInPage } from '@/ui/pages/attendance/CheckInPage'
import { ProfilePage } from '@/ui/pages/profile/ProfilePage'
import { MembresiaPage } from '@/ui/pages/membresia/MembresiaPage'
import { HistorialPage } from '@/ui/pages/historial/HistorialPage'

export default function App() {
  const { refreshToken, setTokens, clear, setInitialized } = useAuthStore()
  const theme = useThemeStore((s) => s.theme)

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
  }, [theme])

  useEffect(() => {
    if (!refreshToken) {
      setInitialized()
      return
    }
    authRepository
      .refresh(refreshToken)
      .then((res) => {
        setTokens(res.access_token, refreshToken)
        const sexo = useAuthStore.getState().user?.sexo ?? null
        useThemeStore.getState().initTheme(sexo)
      })
      .catch(() => clear())
      .finally(() => setInitialized())
  }, [])

  const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined

  const routes = (
    <BrowserRouter>
      <Toaster position="top-center" richColors />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route
          element={
            <ClienteGuard>
              <AppLayout />
            </ClienteGuard>
          }
        >
          <Route path="/home" element={<HomePage />} />
          <Route path="/check-in" element={<CheckInPage />} />
          <Route path="/membresia" element={<MembresiaPage />} />
          <Route path="/historial" element={<HistorialPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
q        <Route path="*" element={<Navigate to="/check-in" replace />} />
      </Routes>
    </BrowserRouter>
  )

  return googleClientId ? (
    <GoogleOAuthProvider clientId={googleClientId}>{routes}</GoogleOAuthProvider>
  ) : routes
}

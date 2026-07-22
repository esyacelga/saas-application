import React from 'react'
import ReactDOM from 'react-dom/client'
import './i18n'
import './index.css'
import 'react-phone-number-input/style.css'
import 'primereact/resources/themes/lara-light-blue/theme.css'
import 'primeicons/primeicons.css'
import { App } from './App'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import { useAuthStore } from '@/infrastructure/store/auth/auth.store'
import { RefreshTokenUseCase } from '@/application/auth/RefreshToken.usecase'

// Restaurar sesión una sola vez antes de montar React.
// Hacerlo aquí evita el doble disparo de useEffect en Strict Mode,
// que podría invalidar un refresh token de rotación única.
const store = useAuthStore.getState()
new RefreshTokenUseCase(authRepository, store)
  .execute()
  .catch(() => {})
  .finally(() => store.setInitialized())

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)

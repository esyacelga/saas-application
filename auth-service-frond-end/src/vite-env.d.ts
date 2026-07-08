/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_API_AUTH_URL: string
  readonly VITE_API_PLATFORM_URL: string
  readonly VITE_API_CORE_URL: string
  readonly VITE_API_ATTENDANCE_URL: string
  readonly VITE_APP_NAME: string
  readonly VITE_PORT: string
  readonly VITE_AVATAR_HOMBRE_URL: string
  readonly VITE_AVATAR_MUJER_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

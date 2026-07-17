interface FBAuthResponse {
  accessToken: string
  expiresIn: number
  signedRequest: string
  userID: string
}

interface FBLoginResponse {
  status: 'connected' | 'not_authorized' | 'unknown'
  authResponse?: FBAuthResponse
}

interface FBInitOptions {
  appId: string
  version: string
  xfbml?: boolean
  cookie?: boolean
}

interface Facebook {
  init(options: FBInitOptions): void
  login(callback: (response: FBLoginResponse) => void, options?: { scope: string }): void
  api(
    path: string,
    params: { fields: string },
    callback: (response: unknown) => void,
  ): void
}

declare global {
  interface Window {
    FB: Facebook
    fbAsyncInit: () => void
  }
}

export {}

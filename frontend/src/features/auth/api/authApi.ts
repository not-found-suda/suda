import { clearAuthState, setAuthTokens } from '@/features/auth/model/authStore'
import type { LoginPayload, TokenPair } from '@/features/auth/model/types'
import { httpRequest } from '@/shared/lib/http/httpClient'

export async function login(payload: LoginPayload) {
  const tokenPair = await httpRequest<TokenPair>('/auth/login', {
    method: 'POST',
    data: payload,
  })
  setAuthTokens(tokenPair)
  return tokenPair
}

export function logout() {
  clearAuthState()
}

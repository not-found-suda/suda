import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens,
} from '@/features/auth/model/tokenStore'
import type { AuthState, TokenPair, User } from '@/features/auth/model/types'

const listeners = new Set<() => void>()

let state: AuthState = {
  accessToken: getAccessToken(),
  refreshToken: getRefreshToken(),
  user: null,
}

function notify() {
  for (const listener of listeners) {
    listener()
  }
}

export function getAuthState() {
  return state
}

export function subscribeAuth(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function setAuthTokens(tokenPair: TokenPair) {
  setTokens(tokenPair)
  state = {
    ...state,
    accessToken: tokenPair.accessToken,
    refreshToken: tokenPair.refreshToken ?? null,
  }
  notify()
}

export function setAuthUser(user: User | null) {
  state = {
    ...state,
    user,
  }
  notify()
}

export function clearAuthState() {
  clearTokens()
  state = {
    accessToken: null,
    refreshToken: null,
    user: null,
  }
  notify()
}

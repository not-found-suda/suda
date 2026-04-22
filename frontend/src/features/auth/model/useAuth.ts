import {
  clearAuthState,
  getAuthState,
  subscribeAuth,
} from '@/features/auth/model/authStore'
import { useSyncExternalStore } from 'react'

export function useAuth() {
  const authState = useSyncExternalStore(subscribeAuth, getAuthState, getAuthState)

  return {
    ...authState,
    isAuthenticated: Boolean(authState.accessToken),
    logout: clearAuthState,
  }
}

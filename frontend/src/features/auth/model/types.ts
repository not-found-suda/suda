export interface TokenPair {
  accessToken: string
  refreshToken?: string
}

export interface LoginPayload {
  email: string
  password: string
}

export interface User {
  id: string
  name: string
  email: string
}

export interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: User | null
}

import { defineStore } from 'pinia'
import { changePassword as changePasswordApi, getMe, login as loginApi } from '../api/auth'
import { clearToken, getToken, setToken } from '../api/http'
import type { AdminInfo, ChangePasswordPayload, LoginResult } from '../types/auth'

interface AuthState {
  token: string | null
  admin: AdminInfo | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({ token: getToken(), admin: null }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token)
  },
  actions: {
    async login(username: string, password: string): Promise<void> {
      const result: LoginResult = await loginApi(username, password)
      this.token = result.accessToken
      this.admin = result.admin
      setToken(result.accessToken)
    },
    async hydrate(): Promise<boolean> {
      if (!this.token) return false
      try {
        this.admin = await getMe()
        return true
      } catch {
        this.logout()
        return false
      }
    },
    async changePassword(payload: ChangePasswordPayload): Promise<void> {
      const result = await changePasswordApi(payload)
      this.token = result.accessToken
      this.admin = result.admin
      setToken(result.accessToken)
    },
    logout(): void {
      this.token = null
      this.admin = null
      clearToken()
    }
  }
})

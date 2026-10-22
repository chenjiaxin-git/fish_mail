import { getData, postData } from './http'
import type { AdminInfo, ChangePasswordPayload, LoginResult } from '../types/auth'

export function login(username: string, password: string): Promise<LoginResult> {
  return postData<LoginResult>('/admin/login', { username, password })
}

export function getMe(): Promise<AdminInfo> {
  return getData<AdminInfo>('/admin/me')
}

export function changePassword(payload: ChangePasswordPayload): Promise<LoginResult> {
  return postData<LoginResult>('/admin/change-password', payload)
}

export interface AdminInfo {
  id: number
  username: string
  status: string
}

export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
  admin: AdminInfo
}

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId?: string
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
  confirmNewPassword: string
}

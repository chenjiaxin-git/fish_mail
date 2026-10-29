import axios, { AxiosError, type AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '../types/auth'

const TOKEN_KEY = 'fishmall.admin.token'

export class ApiError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export const http: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  config.headers['X-Trace-Id'] = crypto.randomUUID?.() || String(Date.now())
  return config
})

http.interceptors.response.use(
  async (response) => {
    if (response.config.responseType === 'blob') {
      if (String(response.headers['content-type']).includes('spreadsheetml.sheet')) return response
      throw await blobError(response.data as Blob)
    }
    const body = response.data as ApiResponse<unknown>
    if (body.code !== '0') {
      return Promise.reject(new ApiError(body.code, body.message || '请求失败'))
    }
    return response
  },
  async (error: AxiosError<ApiResponse<unknown> | Blob>) => {
    if (error.response?.status === 401) {
      clearToken()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }
    const body = error.response?.data
    const failure = body instanceof Blob ? await blobError(body) : new ApiError(body?.code || 'NETWORK_ERROR', body?.message || '网络异常，请稍后重试')
    if (!error.config?.silent) ElMessage.error(failure.message)
    return Promise.reject(failure)
  }
)

export async function getData<T>(url: string): Promise<T> {
  const response = await http.get<ApiResponse<T>>(url)
  return response.data.data
}

export async function postData<T>(url: string, data?: unknown): Promise<T> {
  const response = await http.post<ApiResponse<T>>(url, data)
  return response.data.data
}

export async function putData<T>(url: string, data?: unknown): Promise<T> {
  const response = await http.put<ApiResponse<T>>(url, data)
  return response.data.data
}

export async function deleteData<T>(url: string): Promise<T> {
  const response = await http.delete<ApiResponse<T>>(url)
  return response.data.data
}

export async function uploadFile<T>(url: string, file: File): Promise<T> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post<ApiResponse<T>>(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return response.data.data
}

async function blobError(blob: Blob): Promise<ApiError> {
  try { const body:unknown=JSON.parse(await blob.text()); if(body && typeof body==='object' && 'message' in body && typeof body.message==='string') return new ApiError('code' in body?String(body.code):'EXPORT_ERROR',body.message) } catch { /* 不展示原始响应 */ }
  return new ApiError('EXPORT_ERROR','导出失败，请稍后重试')
}

const config = require('../config')
const auth = require('./auth')

const NETWORK_ERROR_MESSAGE = '网络异常，请稍后重试'

function buildUrl(path) {
  if (/^https?:\/\//i.test(path)) return path
  return config.apiBaseUrl.replace(/\/$/, '') + path
}

function createApiError(body, statusCode) {
  const message = body && body.message ? String(body.message) : ''
  const code = body && body.code ? String(body.code) : ''
  const error = new Error(message || NETWORK_ERROR_MESSAGE)
  error.code = code || 'HTTP_' + statusCode
  return error
}

function request(options) {
  const settings = options || {}
  const header = Object.assign({}, settings.header)
  const token = settings.auth === false ? '' : auth.getToken()
  if (token) header.Authorization = 'Bearer ' + token

  return new Promise((resolve, reject) => {
    wx.request({
      url: buildUrl(settings.url || ''),
      method: settings.method || 'GET',
      data: settings.data || {},
      header,
      timeout: settings.timeout || config.requestTimeout,
      success(response) {
        const body = response.data
        const isSuccessStatus = response.statusCode >= 200 && response.statusCode < 300
        const isSuccessCode = body && String(body.code) === '0'
        if (isSuccessStatus && isSuccessCode) {
          resolve(body.data)
          return
        }
        reject(createApiError(body, response.statusCode))
      },
      fail() {
        reject(new Error(NETWORK_ERROR_MESSAGE))
      }
    })
  })
}

module.exports = { NETWORK_ERROR_MESSAGE, request }

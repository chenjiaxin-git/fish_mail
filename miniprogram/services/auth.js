const config = require('../config')

const TOKEN_KEY = 'fishmall.miniprogram.token'

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function setToken(token) {
  if (token) wx.setStorageSync(TOKEN_KEY, token)
}

function clearToken() {
  wx.removeStorageSync(TOKEN_KEY)
}

function login() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(loginResult) {
        if (!loginResult.code) {
          reject(new Error('微信登录失败，请稍后重试'))
          return
        }
        wx.request({
          url: config.apiBaseUrl.replace(/\/$/, '') + '/api/v1/auth/wechat',
          method: 'POST',
          data: { code: loginResult.code },
          timeout: config.requestTimeout,
          success(response) {
            const body = response.data
            if (response.statusCode >= 200 && response.statusCode < 300 && body && String(body.code) === '0') {
              const data = body.data || {}
              setToken(data.accessToken || data.token || '')
              resolve(data)
              return
            }
            reject(new Error(body && body.message ? String(body.message) : '微信登录失败，请稍后重试'))
          },
          fail() {
            reject(new Error('网络异常，请稍后重试'))
          }
        })
      },
      fail() {
        reject(new Error('微信登录失败，请稍后重试'))
      }
    })
  })
}

function ensureLogin() {
  return getToken() ? Promise.resolve(getToken()) : login().then(() => getToken())
}

module.exports = { TOKEN_KEY, getToken, setToken, clearToken, login, ensureLogin }

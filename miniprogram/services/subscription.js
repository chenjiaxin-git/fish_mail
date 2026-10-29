const { request } = require('./request')
let templateIds = []
function loadConfig() {
  return request({ url: '/users/subscription-config' }).then(config => {
    templateIds = config && config.enabled ? [...new Set(Object.values(config.templates || {}).filter(id => typeof id === 'string' && id))].slice(0, 3) : []
  }).catch(() => { templateIds = [] })
}
// Must be invoked directly from the user's tap handler, before awaiting another API.
function requestOnce() {
  if (!templateIds.length || typeof wx.requestSubscribeMessage !== 'function') return Promise.resolve()
  return new Promise(resolve => {
    try { wx.requestSubscribeMessage({ tmplIds: templateIds.slice(), success: resolve, fail: resolve }) }
    catch (_) { resolve() }
  })
}
module.exports = { loadConfig, requestOnce }

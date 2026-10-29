const { request } = require('./request')

function prepay(id, quoteVersion) {
  return request({ url: '/api/v1/orders/' + id + '/prepay', method: 'POST', data: { quoteVersion } })
}
function syncPayment(id) { return request({ url: '/api/v1/orders/' + id + '/sync-payment', method: 'POST' }) }
function requestPayment(params) {
  return new Promise((resolve, reject) => {
    wx.requestPayment(Object.assign({}, params, {
      success: () => resolve(),
      fail: result => {
        const cancelled = /cancel/i.test(result.errMsg || '')
        const error = new Error(cancelled ? '已取消支付，可稍后继续付款' : '支付未完成，请刷新订单后重试')
        error.cancelled = cancelled
        reject(error)
      }
    }))
  })
}
module.exports = { prepay, syncPayment, requestPayment }

const { request } = require('./request')

function createOrder(data) {
  return request({ url: '/api/v1/orders', method: 'POST', data })
}

function getOrders(data) { return request({ url: '/api/v1/orders', data }) }
function getOrderDetail(id) { return request({ url: '/api/v1/orders/' + id }) }
function confirmQuote(id, quoteVersion) {
  return request({ url: '/api/v1/orders/' + id + '/confirm-quote', method: 'POST', data: { quoteVersion } })
}
function cancelOrder(id) {
  return request({ url: '/api/v1/orders/' + id + '/cancel', method: 'POST', data: { reason: '用户取消订单' } })
}
module.exports = { createOrder, getOrders, getOrderDetail, confirmQuote, cancelOrder }

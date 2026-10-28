const { request } = require('./request')

function createOrder(data) {
  return request({ url: '/api/v1/orders', method: 'POST', data })
}

module.exports = { createOrder }

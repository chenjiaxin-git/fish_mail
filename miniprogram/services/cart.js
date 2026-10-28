const { request } = require('./request')

function getCart() {
  return request({ url: '/api/v1/cart', method: 'GET' })
}

function addCart(productSpecId, quantity) {
  return request({ url: '/api/v1/cart', method: 'POST', data: { productSpecId, quantity } })
}

function updateCartItem(id, quantity) {
  return request({ url: '/api/v1/cart/' + encodeURIComponent(id), method: 'PUT', data: { quantity } })
}

function deleteCartItem(id) {
  return request({ url: '/api/v1/cart/' + encodeURIComponent(id), method: 'DELETE' })
}

module.exports = { getCart, addCart, updateCartItem, deleteCartItem }

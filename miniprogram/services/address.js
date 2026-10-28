const { request } = require('./request')

function getAddresses() {
  return request({ url: '/api/v1/addresses', method: 'GET' })
}

function createAddress(data) {
  return request({ url: '/api/v1/addresses', method: 'POST', data })
}

function updateAddress(id, data) {
  return request({ url: '/api/v1/addresses/' + encodeURIComponent(id), method: 'PUT', data })
}

function deleteAddress(id) {
  return request({ url: '/api/v1/addresses/' + encodeURIComponent(id), method: 'DELETE' })
}

module.exports = { getAddresses, createAddress, updateAddress, deleteAddress }

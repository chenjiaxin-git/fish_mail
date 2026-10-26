const { request } = require('./request')

function getBanners() {
  return request({ url: '/api/v1/banners', method: 'GET' })
}

function getCategories() {
  return request({ url: '/api/v1/categories', method: 'GET' })
}

function getProducts(params) {
  const query = params || {}
  const parts = [
    'page=' + encodeURIComponent(query.page || 1),
    'pageSize=' + encodeURIComponent(query.pageSize || 20)
  ]
  if (query.keyword) parts.push('keyword=' + encodeURIComponent(query.keyword))
  if (query.categoryId !== null && query.categoryId !== undefined && query.categoryId !== '') {
    parts.push('categoryId=' + encodeURIComponent(query.categoryId))
  }
  return request({ url: '/api/v1/products?' + parts.join('&'), method: 'GET' })
}

module.exports = { getBanners, getCategories, getProducts }

const config = require('../config')

function toFiniteNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function formatMoneyCent(value) {
  const cents = toFiniteNumber(value)
  if (cents === null) return '--'
  return (Math.round(Math.abs(cents)) / 100).toFixed(2).replace(/^/, cents < 0 ? '-' : '')
}

function formatQuantity(value) {
  const quantity = toFiniteNumber(value)
  if (quantity === null) return '--'
  const text = quantity.toFixed(3)
  return text.indexOf('.') >= 0 ? text.replace(/0+$/, '').replace(/\.$/, '') : text
}

function formatDate(value, withTime) {
  if (!value) return '--'
  const date = new Date(String(value).replace(/-/g, '/'))
  if (Number.isNaN(date.getTime())) return '--'
  const pad = (number) => String(number).padStart(2, '0')
  const dateText = date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
  return withTime ? dateText + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes()) : dateText
}

function formatPriceRange(minPriceCent, maxPriceCent) {
  const min = formatMoneyCent(minPriceCent)
  const max = formatMoneyCent(maxPriceCent)
  if (min === '--') return '暂无价格'
  return min === max ? min : min + ' - ' + max
}

function resolveImageUrl(url) {
  if (!url) return ''
  const value = String(url)
  if (/^https?:\/\//i.test(value)) return value
  if (value.indexOf('/') === 0) return config.apiBaseUrl.replace(/\/$/, '') + value
  return value
}

module.exports = { formatMoneyCent, formatQuantity, formatDate, formatPriceRange, resolveImageUrl }

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

function parseDate(value) {
  if (!value) return null
  const text = String(value)
  const date = new Date(text.indexOf('T') >= 0 ? text : text.replace(/-/g, '/'))
  return Number.isNaN(date.getTime()) ? null : date
}

function formatDate(value, withTime) {
  if (!value) return '--'
  let date = parseDate(value)
  if (!date) return '--'
  const pad = (number) => String(number).padStart(2, '0')
  const offsetTime = /(?:Z|[+-]\d{2}:?\d{2})$/.test(String(value))
  if (offsetTime) date = new Date(date.getTime() + 8 * 60 * 60 * 1000)
  const part = (name) => date[(offsetTime ? 'getUTC' : 'get') + name]()
  const dateText = part('FullYear') + '-' + pad(part('Month') + 1) + '-' + pad(part('Date'))
  return withTime ? dateText + ' ' + pad(part('Hours')) + ':' + pad(part('Minutes')) : dateText
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

module.exports = { formatMoneyCent, formatQuantity, formatDate, parseDate, formatPriceRange, resolveImageUrl }

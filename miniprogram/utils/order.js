const { formatMoneyCent, formatQuantity, formatDate, parseDate, resolveImageUrl } = require('./format')
const statusNames = {
  CREATED: '待商家确认', QUOTED: '待确认价格', PENDING_PAYMENT: '待支付',
  PAID_WAIT_DELIVERY: '待配送', DELIVERING: '配送中', COMPLETED: '已完成', CANCELLED: '已取消', CLOSED: '已关闭'
}
const tabs = [
  { name: '全部', status: '' }, { name: '待支付', status: 'QUOTED,PENDING_PAYMENT' },
  { name: '待配送', status: 'PAID_WAIT_DELIVERY' }, { name: '已完成', status: 'COMPLETED' },
  { name: '已取消', status: 'CANCELLED,CLOSED' }
]
function countdown(value, now) {
  const date = parseDate(value)
  if (!date) return ''
  const seconds = Math.ceil((date.getTime() - (now === undefined ? Date.now() : now)) / 1000)
  if (seconds <= 0) return '报价已过期'
  return '剩余 ' + Math.floor(seconds / 60) + '分' + seconds % 60 + '秒'
}
function present(order) {
  const quoted = order.finalAmountCent !== null && order.finalAmountCent !== undefined
  const items = (order.items || []).map((item, index) => Object.assign({}, item, {
    key: item.id == null ? index : item.id,
    imageUrl: resolveImageUrl(item.productImageUrl),
    estimatedText: formatQuantity(item.estimatedQuantity),
    actualText: item.actualQuantity == null ? '待商家称重' : formatQuantity(item.actualQuantity),
    priceText: formatMoneyCent(item.unitPriceCent),
    estimatedAmountText: formatMoneyCent(item.estimatedAmountCent),
    finalAmountText: item.finalAmountCent == null ? '待商家称重' : formatMoneyCent(item.finalAmountCent)
  }))
  return Object.assign({}, order, {
    items, statusText: statusNames[order.status] || '未知状态',
    amountLabel: quoted ? '最终金额' : '预估金额',
    canPay: order.status === 'PENDING_PAYMENT' && !!parseDate(order.payDeadlineAt) && Date.now() < parseDate(order.payDeadlineAt).getTime(),
    amountText: formatMoneyCent(quoted ? order.finalAmountCent : order.estimatedAmountCent),
    estimatedAmountText: formatMoneyCent(order.estimatedAmountCent),
    finalAmountText: quoted ? formatMoneyCent(order.finalAmountCent) : '待商家称重',
    createdText: formatDate(order.createdAt, true), deadlineText: formatDate(order.payDeadlineAt, true),
    countdownText: ['QUOTED', 'PENDING_PAYMENT'].includes(order.status) ? countdown(order.payDeadlineAt) : '',
    priceRevisions: (order.priceRevisions || []).map(r => Object.assign({}, r, {
      previousText: formatMoneyCent(r.previousAmountCent), finalText: formatMoneyCent(r.finalAmountCent),
      createdText: formatDate(r.createdAt, true),
      items: (r.items || []).map(i => Object.assign({}, i, { quantityText: formatQuantity(i.actualQuantity), amountText: formatMoneyCent(i.finalAmountCent) }))
    })),
    timeline: [['createdAt', '提交订单'], ['quotedAt', '商家报价'], ['paidAt', '支付成功'],
      ['deliveredAt', '开始配送'], ['completedAt', '订单完成'], ['cancelledAt', '订单取消'], ['closedAt', '订单关闭']]
      .filter(([key]) => order[key]).map(([key, label]) => ({ label, time: formatDate(order[key], true) }))
  })
}
module.exports = { statusNames, tabs, countdown, present }

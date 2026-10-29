const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const format = require('../miniprogram/utils/format')
const orderUtils = require('../miniprogram/utils/order')

function deferred() {
  let resolve, reject
  const promise = new Promise((a, b) => { resolve = a; reject = b })
  return { promise, resolve, reject }
}
function page(file, service, wx = {}, payment = {}, timers = {}) {
  let definition
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../miniprogram/pages', file), 'utf8'), {
    require(name) {
      if (name.endsWith('services/auth')) return { ensureLogin: () => Promise.resolve() }
      if (name.endsWith('services/order')) return service
      if (name.endsWith('services/payment')) return payment
      if (name.endsWith('utils/order')) return orderUtils
      if (name.endsWith('utils/format')) return format
      throw Error(name)
    },
    Page(value) { definition = value }, wx: Object.assign({ showToast() {} }, wx),
    setInterval() { return 1 }, clearInterval() {},
    setTimeout: timers.setTimeout || setTimeout, clearTimeout: timers.clearTimeout || clearTimeout, Date, console
  })
  definition.data = JSON.parse(JSON.stringify(definition.data))
  definition.setData = function (values) { Object.assign(this.data, values) }
  definition.onLoad(file.startsWith('orders/') ? {} : { id: '10' })
  definition.visible = true
  return definition
}

test('zero final price and actual quantity remain visible, null values remain pending', () => {
  const result = orderUtils.present({ status: 'QUOTED', estimatedAmountCent: 9000, finalAmountCent: 0,
    items: [{ estimatedQuantity: 1.5, actualQuantity: 0, finalAmountCent: 0 }] })
  assert.equal(result.amountLabel, '最终金额')
  assert.equal(result.amountText, '0.00')
  assert.equal(result.items[0].actualText, '0')
  assert.equal(result.items[0].finalAmountText, '0.00')
  const pending = orderUtils.present({ status: 'CREATED', estimatedAmountCent: 9000, finalAmountCent: null, items: [{}] })
  assert.equal(pending.amountText, '90.00')
  assert.equal(pending.items[0].actualText, '待商家称重')
})

test('offset ISO times display in Shanghai and deadline boundary expires exactly', () => {
  const deadline = '2026-10-29T12:00:00+08:00'
  const timestamp = Date.parse(deadline)
  assert.equal(format.formatDate(deadline, true), '2026-10-29 12:00')
  assert.equal(format.formatDate('2026-10-29T04:00:00Z', true), '2026-10-29 12:00')
  assert.equal(orderUtils.countdown(deadline, timestamp), '报价已过期')
  assert.equal(orderUtils.countdown(deadline, timestamp - 1000), '剩余 0分1秒')
  assert.equal(format.parseDate('invalid'), null)
  assert.equal(format.formatDate('invalid', true), '--')
  assert.equal(orderUtils.statusNames.CLOSED, '已关闭')
})

test('slow previous list response cannot replace new filter results', async () => {
  const first = deferred(), second = deferred()
  let calls = 0
  const p = page('orders/orders.js', { getOrders: () => (++calls === 1 ? first.promise : second.promise) })
  const old = p.load(true)
  await new Promise(setImmediate)
  p.data.active = 1
  const current = p.load(true)
  await new Promise(setImmediate)
  second.resolve({ total: 1, items: [{ id: 2, status: 'QUOTED' }] })
  await current
  first.resolve({ total: 1, items: [{ id: 1, status: 'CREATED' }] })
  await old
  assert.equal(p.data.orders[0].id, 2)
  assert.equal(p.data.loading, false)
})

test('failed refresh retry loads first page while failed pagination retries same page', async () => {
  let reject = true
  const pages = []
  const p = page('orders/orders.js', { getOrders: async params => {
    pages.push(params.page)
    if (reject) throw Error('网络失败')
    return { total: 40, items: [{ id: params.page, status: 'CREATED' }] }
  } })
  p.page = 2
  await p.load(true)
  reject = false
  await p.load(p.failedReset)
  assert.deepEqual(pages, [1, 1])
  reject = true
  await p.load(false)
  reject = false
  await p.load(p.failedReset)
  assert.deepEqual(pages, [1, 1, 2, 2])
})

test('confirmation locks cancel and refreshes stale quote without automatically confirming it', async () => {
  let modal, confirms = 0, reads = 0
  const p = page('order-detail/order-detail.js', {
    confirmQuote: async (id, version) => { confirms++; assert.equal(version, 1); throw Error('报价已变更') },
    getOrderDetail: async () => { reads++; return { id: 10, status: 'QUOTED', quoteVersion: 2, canConfirmQuote: true } }
  }, { showModal(options) { modal = options } })
  p.data.order = { status: 'QUOTED', quoteVersion: 1, canConfirmQuote: true, canCancel: true }
  p.onConfirm()
  assert.equal(p.data.submitting, true)
  p.onCancel()
  assert.equal(modal.title, '确认价格并支付')
  await modal.success({ confirm: true })
  assert.equal(confirms, 1)
  assert.equal(reads, 1)
  assert.equal(p.data.order.quoteVersion, 2)
  assert.equal(p.data.submitting, false)
})

test('cancelled modal unlocks actions and user cannot cancel a paid order', () => {
  let modal
  const p = page('order-detail/order-detail.js', {}, { showModal(options) { modal = options } })
  p.data.order = { status: 'CREATED', canCancel: true }
  p.onCancel()
  modal.success({ confirm: false })
  assert.equal(p.data.submitting, false)
  p.data.order = { status: 'PAID_WAIT_DELIVERY', canCancel: false }
  modal = null
  p.onCancel()
  assert.equal(modal, null)
})

test('page configuration keeps home as launch page and registers order tab and detail files', () => {
  const root = path.join(__dirname, '../miniprogram')
  const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
  assert.equal(app.pages[0], 'pages/home/home')
  assert.ok(app.tabBar.list.some(tab => tab.pagePath === 'pages/orders/orders'))
  for (const name of app.pages) for (const suffix of ['.js', '.json', '.wxml', '.wxss']) {
    assert.ok(fs.existsSync(path.join(root, name + suffix)), name + suffix)
  }
})

test('cashier success only shows pending confirmation until trusted backend reports paid', async () => {
  let cashierCalls = 0
  const pending = { id: 10, status: 'PENDING_PAYMENT', quoteVersion: 1, finalAmountCent: 3600 }
  const p = page('order-detail/order-detail.js', {}, {}, {
    prepay: async () => ({ paymentParams: { package: 'prepay_id=test', signType: 'RSA' } }),
    requestPayment: async params => { cashierCalls++; assert.equal(params.package, 'prepay_id=test') },
    syncPayment: async () => Object.assign({}, pending, { status: 'PAID_WAIT_DELIVERY' })
  })
  p.data.order = pending
  await p.pay(1)
  assert.equal(cashierCalls, 1)
  assert.equal(p.data.order.status, 'PENDING_PAYMENT')
  assert.match(p.data.paymentMessage, /确认中/)
  await p.pollPayment()
  assert.equal(p.data.order.status, 'PAID_WAIT_DELIVERY')
  assert.equal(p.data.paymentMessage, '支付成功')
})

test('cashier cancellation does not cancel business order or mark it paid', async () => {
  let cancelCalls = 0
  const p = page('order-detail/order-detail.js', { cancelOrder: () => { cancelCalls++ } }, {}, {
    prepay: async () => ({ paymentParams: {} }),
    requestPayment: async () => { const e = Error('已取消支付，可稍后继续付款'); e.cancelled = true; throw e }
  })
  p.data.order = { status: 'PENDING_PAYMENT' }
  await assert.rejects(p.pay(1), /已取消支付/)
  assert.equal(p.data.order.status, 'PENDING_PAYMENT')
  assert.equal(cancelCalls, 0)
  assert.equal(p.data.paymentMessage, '已取消支付，可稍后继续付款')
})

test('payment polling is bounded and page hiding stops further requests', async () => {
  let calls = 0
  const p = page('order-detail/order-detail.js', {}, {}, {
    syncPayment: async () => { calls++; return { id: 10, status: 'PENDING_PAYMENT' } }
  }, { setTimeout: callback => setImmediate(callback), clearTimeout: clearImmediate })
  await p.pollPayment()
  assert.equal(calls, 3)
  p.visible = false
  await p.pollPayment()
  assert.equal(calls, 3)
})

test('hiding during an inflight payment query discards its response', async () => {
  const result = deferred()
  const p = page('order-detail/order-detail.js', {}, {}, { syncPayment: () => result.promise })
  p.data.order = { status: 'PENDING_PAYMENT' }
  const loading = p.pollPayment()
  p.onHide()
  result.resolve({ status: 'PAID_WAIT_DELIVERY' })
  await loading
  assert.equal(p.data.order.status, 'PENDING_PAYMENT')
  assert.notEqual(p.data.paymentMessage, '支付成功')
})

test('prepay failure after confirmation retries payment without confirming quote again', async () => {
  let modal, confirmations = 0, attempts = 0, cashier = 0
  const confirmed = { id: 10, status: 'PENDING_PAYMENT', quoteVersion: 1, finalAmountCent: 3600 }
  const p = page('order-detail/order-detail.js', {
    confirmQuote: async () => { confirmations++; return confirmed }, getOrderDetail: async () => confirmed
  }, { showModal: options => { modal = options } }, {
    prepay: async () => { attempts++; if (attempts === 1) throw Error('网络失败'); return { paymentParams: {} } },
    requestPayment: async () => { cashier++ }
  })
  p.pollPayment = () => {}
  p.data.order = { status: 'QUOTED', canConfirmQuote: true, quoteVersion: 1, finalAmountText: '36.00' }
  p.onConfirm(); await modal.success({ confirm: true })
  assert.equal(p.data.order.status, 'PENDING_PAYMENT')
  assert.equal(p.data.submitting, false)
  p.onPay(); await modal.success({ confirm: true })
  assert.equal(confirmations, 1)
  assert.equal(attempts, 2)
  assert.equal(cashier, 1)
})

test('closed order shows close time and cannot confirm pay or cancel', () => {
  const order = orderUtils.present({ status: 'CLOSED', canConfirmQuote: false, canCancel: false,
    closedAt: '2026-10-29T12:00:00+08:00', payDeadlineAt: '2026-10-29T11:00:00+08:00' })
  assert.equal(order.statusText, '已关闭')
  assert.equal(order.canPay, false)
  assert.equal(order.canConfirmQuote, false)
  assert.equal(order.canCancel, false)
  assert.equal(order.countdownText, '')
  assert.ok(order.timeline.some(item => item.label === '订单关闭' && item.time === '2026-10-29 12:00'))
  assert.ok(orderUtils.tabs.find(tab => tab.name === '已取消').status.includes('CLOSED'))
})

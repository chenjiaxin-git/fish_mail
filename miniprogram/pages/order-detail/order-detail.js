const auth = require('../../services/auth')
const service = require('../../services/order')
const payment = require('../../services/payment')
const { present, countdown } = require('../../utils/order')
const { parseDate } = require('../../utils/format')

Page({
  data: { order: null, loading: false, submitting: false, error: '', inaccessible: false, paymentMessage: '' },
  onLoad(options) { this.id = /^[1-9]\d*$/.test(options.id || '') ? options.id : null; this.sequence = 0 },
  onShow() { this.visible = true; this.load(); this.startTimer(); if (this.awaitingPayment && !this.data.submitting) this.pollPayment() },
  onHide() { this.visible = false; this.sequence++; this.stopTimer(); this.stopPolling() },
  onUnload() { this.visible = false; this.sequence++; this.stopTimer(); this.stopPolling() },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },
  onRetry() { this.load() },
  onAllOrders() { wx.switchTab({ url: '/pages/orders/orders' }) },
  async load() {
    if (this.data.submitting) return
    const sequence = ++this.sequence
    if (!this.id) { this.setData({ error: '订单编号不合法', inaccessible: true }); return }
    this.setData({ loading: true, error: '' })
    try {
      await auth.ensureLogin()
      if (sequence !== this.sequence) return
      const order = await service.getOrderDetail(this.id)
      if (sequence === this.sequence) {
        const values = { order: present(order), loading: false, inaccessible: false }
        if (['PAID_WAIT_DELIVERY', 'DELIVERING', 'COMPLETED'].includes(order.status)) {
          this.awaitingPayment = false; values.paymentMessage = '支付成功'
        }
        this.setData(values)
      }
    } catch (error) {
      if (sequence !== this.sequence) return
      const inaccessible = error.code === 'ORDER_NOT_FOUND'
      this.setData({ error: inaccessible ? '订单不存在或无法访问' : error.message || '订单加载失败', loading: false,
        inaccessible, order: inaccessible ? null : this.data.order })
    }
  },
  onConfirm() {
    if (this.data.submitting || this.data.loading || !this.data.order || !this.data.order.canConfirmQuote) return
    const order = this.data.order
    this.perform('确认价格并支付', '最终金额 ¥' + order.finalAmountText + '，确认后将打开微信收银台。',
      async () => {
        const confirmed = await service.confirmQuote(this.id, order.quoteVersion)
        this.setData({ order: present(confirmed) })
        await this.pay(confirmed.quoteVersion)
        return service.getOrderDetail(this.id)
      }, '')
  },
  onPay() {
    const order = this.data.order
    if (!order || order.status !== 'PENDING_PAYMENT' || this.data.loading || this.data.submitting) return
    this.perform('立即支付', '支付金额 ¥' + order.finalAmountText,
      async () => { await this.pay(order.quoteVersion); return service.getOrderDetail(this.id) }, '')
  },
  async pay(version) {
    const result = await payment.prepay(this.id, version)
    this.awaitingPayment = true
    this.setData({ paymentMessage: '等待微信支付结果' })
    try { await payment.requestPayment(result.paymentParams) }
    catch (error) {
      this.setData({ paymentMessage: error.message })
      // 收银台取消不代表业务订单取消，也不能作为后端未收款的证据。
      throw error
    }
    this.setData({ paymentMessage: '支付结果确认中，请以订单状态为准' })
  },
  async pollPayment() {
    this.stopPolling()
    const generation = this.pollGeneration
    for (let attempt = 0; attempt < 3; attempt++) {
      if (!this.visible || generation !== this.pollGeneration) return
      try {
        const order = await payment.syncPayment(this.id)
        if (!this.visible || generation !== this.pollGeneration) return
        this.sequence++
        this.setData({ order: present(order), loading: false })
        if (['PAID_WAIT_DELIVERY', 'DELIVERING', 'COMPLETED'].includes(order.status)) {
          this.awaitingPayment = false
          this.setData({ paymentMessage: '支付成功' }); return
        }
        if (['CANCELLED', 'CLOSED'].includes(order.status)) { this.awaitingPayment = false; this.setData({ paymentMessage: '' }); return }
      } catch (error) {
        if (this.visible && generation === this.pollGeneration) this.setData({ paymentMessage: '支付结果确认中，请稍后刷新' })
      }
      if (attempt < 2) await new Promise(resolve => { this.pollResolve = resolve; this.pollTimer = setTimeout(resolve, 3500) })
    }
  },
  stopPolling() {
    this.pollGeneration = (this.pollGeneration || 0) + 1
    if (this.pollTimer) clearTimeout(this.pollTimer)
    if (this.pollResolve) this.pollResolve()
    this.pollResolve = null; this.pollTimer = null
  },
  onCancel() {
    if (this.data.submitting || this.data.loading || !this.data.order || !this.data.order.canCancel) return
    this.perform('取消订单', '确认取消订单？取消后将释放商品预占库存。', () => service.cancelOrder(this.id), '订单已取消')
  },
  perform(title, content, action, successText) {
    this.stopPolling()
    this.setData({ submitting: true })
    // 先使可能在途的读取失效，防止动作结果被旧详情覆盖。
    this.sequence++
    wx.showModal({ title, content,
      success: async (result) => {
        if (!result.confirm) { this.setData({ submitting: false }); return }
        try {
          const order = await action()
          if (this.visible) {
            this.setData({ order: present(order) })
            if (successText) wx.showToast({ title: successText, icon: 'success' })
          }
        } catch (error) {
          if (this.visible) wx.showToast({ title: error.message || '操作失败，请重试', icon: 'none' })
          this.setData({ submitting: false })
          if (this.visible) await this.load()
        } finally {
          this.setData({ submitting: false })
          if (this.visible && this.awaitingPayment) this.pollPayment()
        }
      },
      fail: () => this.setData({ submitting: false })
    })
  },
  startTimer() {
    this.stopTimer()
    this.timer = setInterval(() => {
      const o = this.data.order
      if (!this.visible || !o) return
      const deadline = parseDate(o.payDeadlineAt)
      this.setData({ 'order.countdownText': ['QUOTED', 'PENDING_PAYMENT'].includes(o.status) ? countdown(o.payDeadlineAt) : '',
        'order.canPay': o.status === 'PENDING_PAYMENT' && !!deadline && Date.now() < deadline.getTime(),
        'order.canConfirmQuote': o.canConfirmQuote && !!deadline && Date.now() < deadline.getTime() })
    }, 1000)
  },
  stopTimer() { if (this.timer) clearInterval(this.timer); this.timer = null },
  onImageError(e) { this.setData({ ['order.items[' + e.currentTarget.dataset.index + '].imageUrl']: '' }) }
})

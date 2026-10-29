const auth = require('../../services/auth')
const service = require('../../services/order')
const { tabs, present, countdown } = require('../../utils/order')

Page({
  data: { tabs, active: 0, orders: [], loading: false, error: '', total: 0, done: false },
  onLoad() { this.sequence = 0; this.page = 0 },
  onShow() { this.visible = true; this.load(true); this.startTimer() },
  onHide() { this.visible = false; this.sequence++; this.stopTimer() },
  onUnload() { this.visible = false; this.sequence++; this.stopTimer() },
  onPullDownRefresh() { this.load(true).finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (!this.data.done && !this.data.loading) this.load(false) },
  onTabTap(e) { this.setData({ active: Number(e.currentTarget.dataset.index), orders: [], done: false }); this.load(true) },
  onRetry() { this.load(this.failedReset !== false) },
  onOrderTap(e) { wx.navigateTo({ url: '/pages/order-detail/order-detail?id=' + e.currentTarget.dataset.id }) },
  async load(reset) {
    if (!reset && this.data.loading) return
    const sequence = ++this.sequence
    this.failedReset = reset
    const page = reset ? 1 : this.page + 1
    this.setData({ loading: true, error: '' })
    try {
      await auth.ensureLogin()
      if (sequence !== this.sequence) return
      const params = { page, pageSize: 20 }
      if (tabs[this.data.active].status) params.status = tabs[this.data.active].status
      const result = await service.getOrders(params)
      if (sequence !== this.sequence) return
      const next = result.items.map(present)
      const orders = reset ? next : this.data.orders.concat(next)
      this.page = page
      this.setData({ orders, total: result.total, done: orders.length >= result.total, loading: false })
    } catch (error) {
      if (sequence === this.sequence) this.setData({ error: error.message || '订单加载失败，请重试', loading: false })
    }
  },
  startTimer() {
    this.stopTimer()
    this.timer = setInterval(() => {
      if (!this.visible) return
      this.setData({ orders: this.data.orders.map(o => Object.assign({}, o, {
        countdownText: ['QUOTED', 'PENDING_PAYMENT'].includes(o.status) ? countdown(o.payDeadlineAt) : ''
      })) })
    }, 1000)
  },
  stopTimer() { if (this.timer) clearInterval(this.timer); this.timer = null },
  onImageError(e) {
    const orders = this.data.orders.slice()
    const order = orders[e.currentTarget.dataset.order]
    if (order) { order.items[e.currentTarget.dataset.item].imageUrl = ''; this.setData({ orders }) }
  }
})

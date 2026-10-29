const subscription = require('../../services/subscription')
const addressService = require('../../services/address')
const orderService = require('../../services/order')
const auth = require('../../services/auth')
const { formatMoneyCent, formatQuantity } = require('../../utils/format')

const ERROR_MESSAGES = {
  STOCK_INSUFFICIENT: '商品库存不足，请调整数量后重试',
  PRODUCT_NOT_AVAILABLE: '部分商品已下架，请调整后重试',
  ADDRESS_NOT_FOUND: '收货地址不可用，请重新选择'
}

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.random() * 16 | 0
    const value = char === 'x' ? random : (random & 0x3 | 0x8)
    return value.toString(16)
  })
}

function decorateItem(item) {
  return Object.assign({}, item, {
    priceText: formatMoneyCent(item.priceCent),
    quantityText: formatQuantity(item.quantity),
    amountText: formatMoneyCent(item.amountCent)
  })
}

Page({
  data: {
    ready: false,
    paramError: '',
    items: [],
    totalCent: 0,
    totalText: '0.00',
    address: null,
    addressError: '',
    remark: '',
    remarkLength: 0,
    submitting: false,
    submitText: '提交订单'
  },

  onLoad() {
    this.idempotencyKey = uuid()
    this.submitting = false
    this.lifecycle = 0
    this.payload = null
    const eventChannel = this.getOpenerEventChannel ? this.getOpenerEventChannel() : null
    if (eventChannel && eventChannel.on) {
      eventChannel.on('checkout-data', (payload) => {
        this.applyPayload(payload)
      })
    }
  },

  onShow() {
    this.visible = true
    auth.ensureLogin()
      .then(() => { subscription.loadConfig(); return this.loadAddresses() })
      .catch((error) => {
        this.setData({
          addressError: error && error.message ? error.message : '登录失败，请稍后重试'
        })
      })
  },

  onHide() { this.visible = false; this.lifecycle++ },

  onUnload() { this.visible = false; this.lifecycle++ },

  applyPayload(payload) {
    if (!payload || !Array.isArray(payload.items) || payload.items.length === 0) {
      this.setData({ paramError: '结算信息缺失，请重新选择商品' })
      return
    }
    this.payload = payload
    const items = payload.items.map(decorateItem)
    let totalCent = 0
    items.forEach((item) => {
      totalCent += item.amountCent
    })
    this.setData({
      ready: true,
      paramError: '',
      items,
      totalCent,
      totalText: formatMoneyCent(totalCent)
    })
  },

  loadAddresses() {
    return addressService.getAddresses()
      .then((list) => {
        const addresses = Array.isArray(list) ? list : []
        let selected = this.data.address
        if (!selected || !addresses.some((item) => item.id === selected.id)) {
          selected = addresses.find((item) => item.isDefault) || addresses[0] || null
        }
        this.setData({
          address: selected,
          addressError: addresses.length === 0 ? '请先添加收货地址' : ''
        })
      })
      .catch((error) => {
        this.setData({
          addressError: error && error.message ? error.message : '地址加载失败，请稍后重试'
        })
      })
  },

  onAddressTap() {
    wx.navigateTo({
      url: '/pages/address/list/list?mode=select',
      success: (result) => {
        result.eventChannel.on('address-selected', (address) => {
          this.setData({ address, addressError: '' })
        })
      }
    })
  },

  onAddAddressTap() {
    wx.navigateTo({ url: '/pages/address/edit/edit' })
  },

  onRemarkInput(event) {
    const value = (event.detail.value || '').slice(0, 500)
    this.setData({ remark: value, remarkLength: value.length })
  },

  onSubmitTap() {
    if (this.submitting || !this.data.ready) return
    if (!this.data.address) {
      wx.showToast({ title: '请先添加收货地址', icon: 'none' })
      return
    }
    const payload = this.payload
    const request = {
      addressId: this.data.address.id,
      remark: this.data.remark || null,
      items: payload.items.map((item) => ({
        productSpecId: item.productSpecId,
        quantity: item.quantity
      })),
      cartItemIds: payload.items.map((item) => item.cartItemId).filter((id) => id !== null && id !== undefined),
      idempotencyKey: this.idempotencyKey
    }
    this.submitting = true
    this.setData({ submitting: true, submitText: '提交中...' })
    const generation = this.lifecycle
    subscription.requestOnce().then(() => {
      if (!this.visible || generation !== this.lifecycle) {
        this.submitting = false
        this.setData({ submitting: false, submitText: '提交订单' })
        return null
      }
      return orderService.createOrder(request)
    })
      .then((order) => {
        if (!order) return
        wx.showToast({ title: '下单成功，等待商家确认', icon: 'success', duration: 1500 })
        setTimeout(() => {
          wx.redirectTo({ url: '/pages/order-detail/order-detail?id=' + order.id })
        }, 1500)
      })
      .catch((error) => {
        this.submitting = false
        this.setData({ submitting: false, submitText: '提交订单' })
        const code = error && error.code ? String(error.code) : ''
        const message = ERROR_MESSAGES[code] || (error && error.message ? error.message : '网络异常，请稍后重试')
        wx.showToast({ title: message, icon: 'none' })
        if (code === 'ADDRESS_NOT_FOUND') {
          this.setData({ address: null })
          this.loadAddresses()
        }
      })
  },

  onRetryTap() {
    this.setData({ paramError: '' })
    wx.navigateBack()
  }
})

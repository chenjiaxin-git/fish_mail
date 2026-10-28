const cartService = require('../../services/cart')
const auth = require('../../services/auth')
const { formatMoneyCent, formatQuantity, resolveImageUrl } = require('../../utils/format')

function roundQuantity(value) {
  return Math.round(Number(value) * 1000) / 1000
}

function itemAmountCent(priceCent, quantity) {
  return Math.round(Number(priceCent) * Number(quantity))
}

function clampQuantity(value, item) {
  const min = item.minQuantityNum
  const max = item.availableQtyNum
  let quantity = Number(value)
  if (!Number.isFinite(quantity)) quantity = min
  if (item.quantityKind === 'PIECE') quantity = Math.round(quantity)
  quantity = roundQuantity(quantity)
  if (max < min) return min
  if (quantity < min) quantity = min
  if (quantity > max) quantity = max
  return quantity
}

function normalizeItem(raw) {
  const item = raw || {}
  const availableQtyNum = Number(item.availableQty)
  const minQuantityNum = Number(item.minQuantity)
  const stepQuantityNum = Number(item.stepQuantity)
  const quantityNum = Number(item.quantity)
  return Object.assign({}, item, {
    availableQtyNum: Number.isFinite(availableQtyNum) && availableQtyNum > 0 ? availableQtyNum : 0,
    minQuantityNum: Number.isFinite(minQuantityNum) && minQuantityNum > 0 ? minQuantityNum : 1,
    stepQuantityNum: Number.isFinite(stepQuantityNum) && stepQuantityNum > 0 ? stepQuantityNum : 1,
    quantityNum: Number.isFinite(quantityNum) && quantityNum > 0 ? quantityNum : 0,
    imageUrl: resolveImageUrl(item.mainImageUrl),
    imageFailed: false,
    priceText: formatMoneyCent(item.priceCent),
    quantityText: formatQuantity(item.quantity),
    amountCent: itemAmountCent(item.priceCent, item.quantity),
    amountText: formatMoneyCent(itemAmountCent(item.priceCent, item.quantity))
  })
}

Page({
  data: {
    loading: true,
    error: '',
    items: [],
    allSelected: false,
    selectedCount: 0,
    totalCent: 0,
    totalText: '0.00',
    checkoutDisabled: true
  },

  onLoad() {
    this.selectedIds = {}
    this.requestLock = false
  },

  onShow() {
    auth.ensureLogin()
      .then(() => this.loadCart(true))
      .catch((error) => {
        this.setData({
          loading: false,
          error: error && error.message ? error.message : '登录失败，请稍后重试'
        })
      })
  },

  onPullDownRefresh() {
    this.loadCart(false).finally(() => wx.stopPullDownRefresh())
  },

  loadCart(showLoading) {
    if (this.requestLock) return Promise.resolve()
    this.requestLock = true
    if (showLoading) this.setData({ loading: true, error: '' })
    return cartService.getCart()
      .then((list) => {
        const items = (Array.isArray(list) ? list : []).map(normalizeItem)
        const nextSelected = {}
        items.forEach((item) => {
          if (item.purchasable) nextSelected[item.id] = true
        })
        this.selectedIds = nextSelected
        this.applyItems(items)
        this.setData({ loading: false, error: '' })
      })
      .catch((error) => {
        const message = error && error.message ? error.message : '网络异常，请稍后重试'
        if (showLoading) {
          this.setData({ loading: false, error: message })
        } else {
          wx.showToast({ title: message, icon: 'none' })
        }
      })
      .finally(() => {
        this.requestLock = false
      })
  },

  applyItems(items) {
    const selectedIds = this.selectedIds
    let selectedCount = 0
    let totalCent = 0
    let purchasableCount = 0
    const decorated = items.map((item) => {
      const selected = item.purchasable && !!selectedIds[item.id]
      if (item.purchasable) purchasableCount += 1
      if (selected) {
        selectedCount += 1
        totalCent += item.amountCent
      }
      return Object.assign({}, item, { selected })
    })
    this.setData({
      items: decorated,
      selectedCount,
      totalCent,
      totalText: formatMoneyCent(totalCent),
      allSelected: purchasableCount > 0 && selectedCount === purchasableCount,
      checkoutDisabled: selectedCount === 0
    })
  },

  noop() {},

  onToggleSelect(event) {
    const id = Number(event.currentTarget.dataset.id)
    const item = this.data.items.find((value) => value.id === id)
    if (!item || !item.purchasable) return
    if (this.selectedIds[id]) {
      delete this.selectedIds[id]
    } else {
      this.selectedIds[id] = true
    }
    this.applyItems(this.data.items)
  },

  onToggleAll() {
    const items = this.data.items
    const allSelected = this.data.allSelected
    const nextSelected = {}
    if (!allSelected) {
      items.forEach((item) => {
        if (item.purchasable) nextSelected[item.id] = true
      })
    }
    this.selectedIds = nextSelected
    this.applyItems(items)
  },

  onStepTap(event) {
    const id = Number(event.currentTarget.dataset.id)
    const direction = Number(event.currentTarget.dataset.direction)
    const item = this.data.items.find((value) => value.id === id)
    if (!item || !item.purchasable) return
    const base = item.quantityNum > 0 ? item.quantityNum : item.minQuantityNum
    const raw = base + direction * item.stepQuantityNum
    const quantity = clampQuantity(raw, item)
    if (direction < 0 && raw < item.minQuantityNum) {
      wx.showToast({ title: '最少购买 ' + formatQuantity(item.minQuantityNum), icon: 'none' })
    }
    if (direction > 0 && item.availableQtyNum >= item.minQuantityNum && raw > item.availableQtyNum) {
      wx.showToast({ title: '已达可购上限', icon: 'none' })
    }
    if (quantity === roundQuantity(base)) return
    this.updateQuantity(item, quantity)
  },

  onQuantityInput(event) {
    const id = Number(event.currentTarget.dataset.id)
    const items = this.data.items.map((item) => {
      if (item.id !== id) return item
      return Object.assign({}, item, { quantityText: event.detail.value || '' })
    })
    this.setData({ items })
  },

  onQuantityConfirm(event) {
    const id = Number(event.currentTarget.dataset.id)
    const item = this.data.items.find((value) => value.id === id)
    if (!item || !item.purchasable) return
    const raw = Number(event.detail.value !== undefined ? event.detail.value : item.quantityText)
    const quantity = clampQuantity(event.detail.value !== undefined ? event.detail.value : item.quantityText, item)
    if (Number.isFinite(raw) && roundQuantity(raw) !== quantity) {
      wx.showToast({ title: '数量已调整为可购范围', icon: 'none' })
    }
    if (quantity === item.quantityNum) {
      const items = this.data.items.map((value) => {
        if (value.id !== id) return value
        return Object.assign({}, value, { quantityText: formatQuantity(quantity) })
      })
      this.applyItems(items)
      return
    }
    this.updateQuantity(item, quantity)
  },

  updateQuantity(item, quantity) {
    if (this.requestLock) return
    this.requestLock = true
    cartService.updateCartItem(item.id, quantity)
      .then((updated) => {
        const next = normalizeItem(updated)
        const items = this.data.items.map((value) => (value.id === item.id ? next : value))
        this.applyItems(items)
      })
      .catch((error) => {
        const message = error && error.message ? error.message : '网络异常，请稍后重试'
        wx.showToast({ title: message, icon: 'none' })
        this.requestLock = false
        this.loadCart(false)
      })
      .finally(() => {
        this.requestLock = false
      })
  },

  onDeleteTap(event) {
    const id = Number(event.currentTarget.dataset.id)
    const item = this.data.items.find((value) => value.id === id)
    if (!item) return
    wx.showModal({
      title: '删除商品',
      content: '确定将“' + (item.productName || '该商品') + '”移出购物车吗？',
      confirmText: '删除',
      cancelText: '再想想',
      success: (result) => {
        if (!result.confirm) return
        this.deleteItem(item.id)
      }
    })
  },

  deleteItem(id) {
    if (this.requestLock) return
    this.requestLock = true
    cartService.deleteCartItem(id)
      .then(() => {
        delete this.selectedIds[id]
        wx.showToast({ title: '已删除', icon: 'none' })
        this.requestLock = false
        return this.loadCart(false)
      })
      .catch((error) => {
        const message = error && error.message ? error.message : '网络异常，请稍后重试'
        wx.showToast({ title: message, icon: 'none' })
      })
      .finally(() => {
        this.requestLock = false
      })
  },

  onItemTap(event) {
    const id = Number(event.currentTarget.dataset.id)
    const item = this.data.items.find((value) => value.id === id)
    if (!item || !item.productId) return
    wx.navigateTo({ url: '/pages/product-detail/product-detail?id=' + item.productId })
  },

  onImageError(event) {
    const id = Number(event.currentTarget.dataset.id)
    const items = this.data.items.map((item) => {
      if (item.id !== id) return item
      return Object.assign({}, item, { imageFailed: true })
    })
    this.setData({ items })
  },

  onCheckoutTap() {
    if (this.data.checkoutDisabled) return
    const selected = this.data.items.filter((item) => item.purchasable && this.selectedIds[item.id])
    if (selected.length === 0) return
    const payload = {
      mode: 'cart',
      items: selected.map((item) => ({
        productSpecId: item.productSpecId,
        quantity: item.quantityNum,
        cartItemId: item.id,
        productName: item.productName,
        specName: item.specName,
        priceCent: item.priceCent,
        quantityKind: item.quantityKind,
        unit: item.unit,
        imageUrl: item.imageUrl,
        amountCent: item.amountCent
      }))
    }
    wx.navigateTo({
      url: '/pages/checkout/checkout',
      success: (result) => {
        result.eventChannel.emit('checkout-data', payload)
      }
    })
  },

  onGoShoppingTap() {
    wx.switchTab({ url: '/pages/home/home' })
  },

  onRetryTap() {
    this.loadCart(true)
  }
})

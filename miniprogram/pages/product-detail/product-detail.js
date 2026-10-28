const catalog = require('../../services/catalog')
const { formatMoneyCent, formatQuantity, formatPriceRange, resolveImageUrl } = require('../../utils/format')

function roundQuantity(value) {
  return Math.round(Number(value) * 1000) / 1000
}

function clampQuantity(value, spec) {
  const min = spec.minQuantityNum
  const max = spec.availableQtyNum
  let quantity = Number(value)
  if (!Number.isFinite(quantity)) quantity = min
  if (spec.quantityKind === 'PIECE') quantity = Math.round(quantity)
  quantity = roundQuantity(quantity)
  if (max < min) return min
  if (quantity < min) quantity = min
  if (quantity > max) quantity = max
  return quantity
}

function normalizeSpec(spec) {
  const item = spec || {}
  const availableQtyNum = Number(item.availableQty)
  const minQuantityNum = Number(item.minQuantity)
  const stepQuantityNum = Number(item.stepQuantity)
  return Object.assign({}, item, {
    availableQtyNum: Number.isFinite(availableQtyNum) && availableQtyNum > 0 ? availableQtyNum : 0,
    minQuantityNum: Number.isFinite(minQuantityNum) && minQuantityNum > 0 ? minQuantityNum : 1,
    stepQuantityNum: Number.isFinite(stepQuantityNum) && stepQuantityNum > 0 ? stepQuantityNum : 1,
    kindText: item.quantityKind === 'PIECE' ? '按件' : '按重量',
    priceText: formatMoneyCent(item.priceCent),
    stockText: formatQuantity(item.availableQty),
    soldOut: !(Number(item.availableQty) > 0)
  })
}

Page({
  data: {
    productId: null,
    loading: true,
    error: '',
    notFound: false,
    product: null,
    specs: [],
    selectedSpecIndex: 0,
    selectedSpec: null,
    quantity: 0,
    quantityText: '',
    imageFailed: false,
    orderDisabled: true,
    orderText: '已售罄，无法下单'
  },

  onLoad(options) {
    const id = Number(options && options.id)
    this.productId = Number.isFinite(id) && id > 0 ? id : null
    this.loaded = false
    this.requestLock = false
    if (!this.productId) {
      this.setData({ loading: false, notFound: true })
      return
    }
    this.setData({ productId: this.productId })
    this.loadDetail(true)
  },

  onShow() {
    if (this.loaded && this.productId) this.loadDetail(false)
  },

  onPullDownRefresh() {
    this.loadDetail(false).finally(() => wx.stopPullDownRefresh())
  },

  loadDetail(showLoading) {
    if (!this.productId || this.requestLock) return Promise.resolve()
    this.requestLock = true
    if (showLoading) this.setData({ loading: true, error: '', notFound: false })
    return catalog.getProductDetail(this.productId)
      .then((detail) => {
        this.loaded = true
        this.applyDetail(detail)
        this.setData({ loading: false, error: '', notFound: false })
      })
      .catch((error) => {
        const message = error && error.message ? error.message : '网络异常，请稍后重试'
        const isNotFound = !!(error && error.code === 'PRODUCT_NOT_FOUND')
        if (showLoading || !this.data.product) {
          this.setData({
            loading: false,
            error: isNotFound ? '' : message,
            notFound: isNotFound
          })
        } else {
          wx.showToast({ title: message, icon: 'none' })
        }
      })
      .finally(() => {
        this.requestLock = false
      })
  },

  applyDetail(detail) {
    const data = detail || {}
    const specs = (Array.isArray(data.specs) ? data.specs : []).map(normalizeSpec)
    let selectedIndex = -1
    for (let i = 0; i < specs.length; i++) {
      if (!specs[i].soldOut) {
        selectedIndex = i
        break
      }
    }
    if (selectedIndex < 0) selectedIndex = specs.length > 0 ? 0 : -1
    const selectedSpec = selectedIndex >= 0 ? specs[selectedIndex] : null
    const quantity = selectedSpec ? clampQuantity(selectedSpec.minQuantityNum, selectedSpec) : 0
    this.setData({
      productId: data.id || this.productId,
      imageFailed: false,
      product: Object.assign({}, data, {
        mainImageUrl: resolveImageUrl(data.mainImageUrl),
        priceText: formatPriceRange(data.minPriceCent, data.maxPriceCent),
        stockText: formatQuantity(data.totalAvailableQty)
      }),
      specs,
      selectedSpecIndex: selectedIndex,
      selectedSpec,
      quantity,
      quantityText: quantity > 0 ? formatQuantity(quantity) : ''
    })
    this.updateOrderState()
  },

  updateOrderState() {
    const spec = this.data.selectedSpec
    if (!spec) {
      this.setData({ orderDisabled: true, orderText: '暂无规格' })
      return
    }
    if (spec.availableQtyNum <= 0) {
      this.setData({ orderDisabled: true, orderText: '已售罄，无法下单' })
      return
    }
    if (spec.availableQtyNum < spec.minQuantityNum) {
      this.setData({ orderDisabled: true, orderText: '库存不足，无法下单' })
      return
    }
    this.setData({ orderDisabled: false, orderText: '立即下单' })
  },

  onSpecTap(event) {
    const index = Number(event.currentTarget.dataset.index)
    const spec = this.data.specs[index]
    if (!spec || index === this.data.selectedSpecIndex) return
    const quantity = clampQuantity(spec.minQuantityNum, spec)
    this.setData({
      selectedSpecIndex: index,
      selectedSpec: spec,
      quantity,
      quantityText: formatQuantity(quantity)
    })
    this.updateOrderState()
  },

  onQuantityMinus() {
    this.stepQuantity(-1)
  },

  onQuantityPlus() {
    this.stepQuantity(1)
  },

  stepQuantity(direction) {
    const spec = this.data.selectedSpec
    if (!spec) return
    const current = Number(this.data.quantity)
    const base = Number.isFinite(current) && current > 0 ? current : spec.minQuantityNum
    const raw = base + direction * spec.stepQuantityNum
    const quantity = clampQuantity(raw, spec)
    if (direction < 0 && raw < spec.minQuantityNum) {
      wx.showToast({ title: '最少购买 ' + formatQuantity(spec.minQuantityNum), icon: 'none' })
    }
    if (direction > 0 && spec.availableQtyNum >= spec.minQuantityNum && raw > spec.availableQtyNum) {
      wx.showToast({ title: '已达可购上限', icon: 'none' })
    }
    this.setData({ quantity, quantityText: formatQuantity(quantity) })
  },

  onQuantityInput(event) {
    this.setData({ quantityText: event.detail.value || '' })
  },

  onQuantityConfirm() {
    const spec = this.data.selectedSpec
    if (!spec) return
    const raw = Number(this.data.quantityText)
    const quantity = clampQuantity(this.data.quantityText, spec)
    if (Number.isFinite(raw) && roundQuantity(raw) !== quantity) {
      wx.showToast({ title: '数量已调整为可购范围', icon: 'none' })
    }
    this.setData({ quantity, quantityText: formatQuantity(quantity) })
  },

  onOrderTap() {
    if (this.data.orderDisabled) return
    wx.showToast({ title: '下单功能暂未开放', icon: 'none' })
  },

  onImageError() {
    this.setData({ imageFailed: true })
  },

  onRetryTap() {
    this.loadDetail(true)
  }
})

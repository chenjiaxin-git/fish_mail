const catalog = require('../../services/catalog')
const { formatPriceRange, formatQuantity, resolveImageUrl } = require('../../utils/format')

const PAGE_SIZE = 20
const MAX_KEYWORD_LENGTH = 100

function normalizeBanner(item) {
  const banner = item || {}
  const targetType = banner.targetType || banner.jumpType || banner.linkType || banner.type || 'NONE'
  const targetId = banner.categoryId || banner.targetCategoryId || banner.targetId || banner.jumpTargetId || null
  return Object.assign({}, banner, {
    imageUrl: resolveImageUrl(banner.imageUrl || banner.mainImageUrl || banner.url || ''),
    imageFailed: false,
    targetType,
    categoryId: targetType === 'CATEGORY' ? targetId : null,
    productId: targetType === 'PRODUCT' ? targetId : null
  })
}

function normalizeProduct(item) {
  const product = item || {}
  return Object.assign({}, product, {
    mainImageUrl: resolveImageUrl(product.mainImageUrl),
    imageFailed: false,
    priceText: formatPriceRange(product.minPriceCent, product.maxPriceCent),
    stockText: formatQuantity(product.totalAvailableQty)
  })
}

Page({
  data: {
    banners: [],
    categories: [],
    products: [],
    keyword: '',
    selectedCategoryId: null,
    page: 1,
    totalProducts: 0,
    hasMore: false,
    metaLoading: false,
    productsLoading: false,
    loadingMore: false,
    metaError: false,
    productsError: ''
  },

  onLoad() {
    this.productRequestLock = false
    this.loadHome()
  },

  onPullDownRefresh() {
    this.loadHome().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.loadMoreProducts()
  },

  onCartTap() {
    wx.switchTab({ url: '/pages/cart/cart' })
  },

  loadHome() {
    this.setData({ metaError: false, productsError: '' })
    return this.loadMeta().then(() => this.loadProducts(1, true))
  },

  loadMeta() {
    if (this.data.metaLoading) return Promise.resolve()
    this.setData({ metaLoading: true, metaError: false })
    return Promise.all([catalog.getBanners(), catalog.getCategories()])
      .then((results) => {
        const banners = (Array.isArray(results[0]) ? results[0] : []).map(normalizeBanner)
        const categories = Array.isArray(results[1]) ? results[1] : []
        this.setData({ banners, categories })
      })
      .catch((error) => {
        const message = error && error.message ? error.message : '网络异常，请稍后重试'
        this.setData({ metaError: true })
        this.showError(message)
      })
      .finally(() => {
        this.setData({ metaLoading: false })
      })
  },

  loadProducts(page, replace) {
    if (this.productRequestLock) return Promise.resolve()
    this.productRequestLock = true
    this.setData({
      productsLoading: replace,
      loadingMore: !replace,
      productsError: ''
    })
    return catalog.getProducts({
      keyword: this.data.keyword.trim(),
      categoryId: this.data.selectedCategoryId,
      page,
      pageSize: PAGE_SIZE
    }).then((result) => {
      const pageData = result || {}
      const items = (Array.isArray(pageData.items) ? pageData.items : []).map(normalizeProduct)
      const products = replace ? items : this.data.products.concat(items)
      const totalProducts = Number(pageData.total) || products.length
      this.setData({
        products,
        page,
        totalProducts,
        hasMore: products.length < totalProducts && items.length > 0,
        productsLoading: false,
        loadingMore: false
      })
    }).catch((error) => {
      const message = error && error.message ? error.message : '网络异常，请稍后重试'
      this.setData({
        productsError: message,
        productsLoading: false,
        loadingMore: false
      })
      this.showError(message)
    }).finally(() => {
      this.productRequestLock = false
    })
  },

  loadMoreProducts() {
    if (!this.data.hasMore || this.data.productsLoading || this.data.loadingMore) return
    this.loadProducts(this.data.page + 1, false)
  },

  onKeywordInput(event) {
    this.setData({ keyword: this.limitKeyword(event.detail.value || '') })
  },

  onKeywordConfirm() {
    this.loadProducts(1, true)
  },

  onSearchTap() {
    this.loadProducts(1, true)
  },

  onClearKeywordTap() {
    this.setData({ keyword: '' })
    this.loadProducts(1, true)
  },

  limitKeyword(value) {
    const keyword = String(value || '')
    if (keyword.length <= MAX_KEYWORD_LENGTH) return keyword
    wx.showToast({ title: '搜索词最多100个字符', icon: 'none' })
    return keyword.slice(0, MAX_KEYWORD_LENGTH)
  },

  onCategoryTap(event) {
    const value = event.currentTarget.dataset.id
    const selectedCategoryId = value === null || value === undefined || value === '' ? null : Number(value)
    if (selectedCategoryId === this.data.selectedCategoryId) return
    this.setData({ selectedCategoryId })
    this.loadProducts(1, true)
  },

  onBannerTap(event) {
    const banner = this.data.banners[event.currentTarget.dataset.index] || {}
    const type = String(banner.targetType || 'NONE').toUpperCase()
    if (type === 'CATEGORY' && banner.categoryId !== null && banner.categoryId !== undefined) {
      const categoryId = Number(banner.categoryId)
      if (Number.isFinite(categoryId)) {
        this.setData({ selectedCategoryId: categoryId })
        this.loadProducts(1, true)
      }
      return
    }
    if (type === 'PRODUCT') {
      const productId = Number(banner.productId)
      if (banner.productId !== null && banner.productId !== undefined && Number.isFinite(productId)) {
        wx.navigateTo({ url: '/pages/product-detail/product-detail?id=' + productId })
      }
    }
  },

  onBannerImageError(event) {
    const index = Number(event.currentTarget.dataset.index)
    this.setData({ ['banners[' + index + '].imageFailed']: true })
  },

  onProductImageError(event) {
    const index = Number(event.currentTarget.dataset.index)
    this.setData({ ['products[' + index + '].imageFailed']: true })
  },

  onProductTap(event) {
    const id = Number(event.currentTarget.dataset.id)
    if (!Number.isFinite(id) || id <= 0) return
    wx.navigateTo({ url: '/pages/product-detail/product-detail?id=' + id })
  },

  onRetryTap() {
    this.loadHome()
  },

  onRetryProductsTap() {
    this.loadProducts(1, true)
  },

  showError(message) {
    wx.showToast({ title: message, icon: 'none' })
  }
})

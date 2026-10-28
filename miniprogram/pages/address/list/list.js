const addressService = require('../../../services/address')
const auth = require('../../../services/auth')

const ADDRESS_LIMIT = 20

Page({
  data: {
    loading: true,
    error: '',
    addresses: [],
    selectMode: false,
    reachedLimit: false
  },

  onLoad(options) {
    this.selectMode = !!(options && options.mode === 'select')
    this.setData({ selectMode: this.selectMode })
    this.requestLock = false
  },

  onShow() {
    auth.ensureLogin()
      .then(() => this.loadAddresses(true))
      .catch((error) => {
        this.setData({
          loading: false,
          error: error && error.message ? error.message : '登录失败，请稍后重试'
        })
      })
  },

  onPullDownRefresh() {
    this.loadAddresses(false).finally(() => wx.stopPullDownRefresh())
  },

  loadAddresses(showLoading) {
    if (this.requestLock) return Promise.resolve()
    this.requestLock = true
    if (showLoading) this.setData({ loading: true, error: '' })
    return addressService.getAddresses()
      .then((list) => {
        const addresses = Array.isArray(list) ? list : []
        this.setData({
          loading: false,
          error: '',
          addresses,
          reachedLimit: addresses.length >= ADDRESS_LIMIT
        })
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

  onItemTap(event) {
    const id = Number(event.currentTarget.dataset.id)
    const address = this.data.addresses.find((item) => item.id === id)
    if (!address) return
    if (this.selectMode) {
      const eventChannel = this.getOpenerEventChannel ? this.getOpenerEventChannel() : null
      if (eventChannel && eventChannel.emit) {
        eventChannel.emit('address-selected', address)
      }
      wx.navigateBack()
      return
    }
    wx.navigateTo({ url: '/pages/address/edit/edit?id=' + address.id })
  },

  onEditTap(event) {
    const id = Number(event.currentTarget.dataset.id)
    wx.navigateTo({ url: '/pages/address/edit/edit?id=' + id })
  },

  onDeleteTap(event) {
    const id = Number(event.currentTarget.dataset.id)
    const address = this.data.addresses.find((item) => item.id === id)
    if (!address) return
    wx.showModal({
      title: '删除地址',
      content: '确定删除该收货地址吗？',
      confirmText: '删除',
      cancelText: '再想想',
      success: (result) => {
        if (!result.confirm) return
        this.deleteAddress(id)
      }
    })
  },

  deleteAddress(id) {
    if (this.requestLock) return
    this.requestLock = true
    addressService.deleteAddress(id)
      .then(() => {
        wx.showToast({ title: '已删除', icon: 'none' })
        this.requestLock = false
        return this.loadAddresses(false)
      })
      .catch((error) => {
        this.requestLock = false
        const message = error && error.message ? error.message : '网络异常，请稍后重试'
        wx.showToast({ title: message, icon: 'none' })
      })
  },

  onAddTap() {
    if (this.data.reachedLimit) {
      wx.showToast({ title: '最多添加 ' + ADDRESS_LIMIT + ' 个收货地址', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/address/edit/edit' })
  },

  onRetryTap() {
    this.loadAddresses(true)
  }
})

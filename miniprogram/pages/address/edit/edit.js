const addressService = require('../../../services/address')
const auth = require('../../../services/auth')

const PHONE_PATTERN = /^1\d{10}$/

Page({
  data: {
    loading: false,
    saving: false,
    saveText: '保存',
    addressId: null,
    contactName: '',
    phone: '',
    detailAddress: '',
    isDefault: false
  },

  onLoad(options) {
    const id = Number(options && options.id)
    this.addressId = Number.isFinite(id) && id > 0 ? id : null
    this.setData({ addressId: this.addressId })
    wx.setNavigationBarTitle({ title: this.addressId ? '编辑收货地址' : '新增收货地址' })
    if (this.addressId) {
      auth.ensureLogin()
        .then(() => this.loadAddress())
        .catch((error) => {
          wx.showToast({ title: error && error.message ? error.message : '登录失败，请稍后重试', icon: 'none' })
        })
    }
  },

  loadAddress() {
    this.setData({ loading: true })
    return addressService.getAddresses()
      .then((list) => {
        const address = (Array.isArray(list) ? list : []).find((item) => item.id === this.addressId)
        this.setData({ loading: false })
        if (!address) {
          wx.showToast({ title: '地址不存在或已删除', icon: 'none' })
          setTimeout(() => wx.navigateBack(), 1200)
          return
        }
        this.setData({
          contactName: address.contactName || '',
          phone: address.phone || '',
          detailAddress: address.detailAddress || '',
          isDefault: !!address.isDefault
        })
      })
      .catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: error && error.message ? error.message : '网络异常，请稍后重试', icon: 'none' })
      })
  },

  onNameInput(event) {
    this.setData({ contactName: (event.detail.value || '').slice(0, 50) })
  },

  onPhoneInput(event) {
    this.setData({ phone: (event.detail.value || '').slice(0, 20) })
  },

  onDetailInput(event) {
    this.setData({ detailAddress: (event.detail.value || '').slice(0, 200) })
  },

  onDefaultChange(event) {
    this.setData({ isDefault: !!event.detail.value })
  },

  onSaveTap() {
    if (this.data.saving) return
    const contactName = (this.data.contactName || '').trim()
    const phone = (this.data.phone || '').trim()
    const detailAddress = (this.data.detailAddress || '').trim()
    if (!contactName) {
      wx.showToast({ title: '请填写联系人姓名', icon: 'none' })
      return
    }
    if (!PHONE_PATTERN.test(phone)) {
      wx.showToast({ title: '请填写正确的 11 位手机号', icon: 'none' })
      return
    }
    if (!detailAddress) {
      wx.showToast({ title: '请填写详细地址', icon: 'none' })
      return
    }
    const payload = {
      contactName,
      phone,
      detailAddress,
      isDefault: this.data.isDefault
    }
    this.setData({ saving: true, saveText: '保存中...' })
    const action = this.addressId
      ? addressService.updateAddress(this.addressId, payload)
      : addressService.createAddress(payload)
    action
      .then(() => {
        wx.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => wx.navigateBack(), 1200)
      })
      .catch((error) => {
        this.setData({ saving: false, saveText: '保存' })
        wx.showToast({ title: error && error.message ? error.message : '网络异常，请稍后重试', icon: 'none' })
      })
  }
})

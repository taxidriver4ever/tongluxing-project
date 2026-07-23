import { createSosEvent } from "../../api/sos"

Page({
  data: {
    loadingLocation: true,
    sending: false,
    latitude: 0,
    longitude: 0,
    accuracy: 0,
    address: "正在获取当前位置…",
    message: ""
  },
  onLoad(options: Record<string, string>) {
    const latitude = Number(options.latitude || 0)
    const longitude = Number(options.longitude || 0)
    if (latitude && longitude) {
      this.setLocation(latitude, longitude, Number(options.accuracy || 0))
      return
    }
    this.locate()
  },
  locate() {
    this.setData({ loadingLocation: true })
    wx.getLocation({
      type: "gcj02",
      isHighAccuracy: true,
      success: result => this.setLocation(result.latitude, result.longitude, result.accuracy || 0),
      fail: () => {
        this.setData({ loadingLocation: false, address: "定位失败，请开启定位权限后重试" })
        wx.showToast({ title: "无法获取当前位置", icon: "none" })
      }
    })
  },
  setLocation(latitude: number, longitude: number, accuracy: number) {
    this.setData({
      loadingLocation: false,
      latitude,
      longitude,
      accuracy,
      address: "当前位置（" + latitude.toFixed(6) + ", " + longitude.toFixed(6) + "）"
    })
  },
  onMessage(event: WechatMiniprogram.Input) { this.setData({ message: event.detail.value }) },
  openMap() {
    if (!this.data.latitude || !this.data.longitude) return
    wx.openLocation({ latitude: this.data.latitude, longitude: this.data.longitude, name: "SOS 当前位置", address: this.data.address })
  },
  async submit() {
    if (this.data.sending) return
    if (!this.data.latitude || !this.data.longitude) {
      wx.showToast({ title: "请先获取当前位置", icon: "none" })
      return
    }
    const confirmation = await wx.showModal({
      title: "确认发送 SOS",
      content: "提交后，平台运营后台会立即看到你的求助位置。当前版本不会自动拨打 110。",
      confirmText: "确认发送",
      confirmColor: "#E5484D"
    })
    if (!confirmation.confirm) return
    this.setData({ sending: true })
    try {
      const result = await createSosEvent({
        requestId: "wx-" + Date.now() + "-" + Math.random().toString(36).slice(2, 8),
        latitude: this.data.latitude,
        longitude: this.data.longitude,
        locationAccuracyMeters: this.data.accuracy,
        address: this.data.address,
        message: this.data.message.trim() || "用户从小程序地图页发起紧急求助"
      })
      await wx.showModal({
        title: "已通知平台运营人员",
        content: "SOS 编号：" + result.id + "\n平台已收到你的位置。如有生命危险，请立即自行拨打 110。",
        showCancel: false,
        confirmText: "知道了"
      })
      wx.navigateBack()
    } catch (error) {
      wx.showToast({ title: error instanceof Error ? error.message : "SOS 发送失败", icon: "none" })
    } finally {
      this.setData({ sending: false })
    }
  }
})

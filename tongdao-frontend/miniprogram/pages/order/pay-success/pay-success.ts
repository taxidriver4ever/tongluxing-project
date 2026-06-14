Component({
  methods: {
    goCode() {
      wx.navigateTo({ url: "/pages/order/verification-code/verification-code" })
    },
    goShare() {
      wx.showToast({ title: "已生成分享卡片", icon: "none" })
    }
  }
})

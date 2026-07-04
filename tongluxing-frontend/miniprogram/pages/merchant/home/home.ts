Component({
  data: {
    metrics: [
      { value: "26", label: "今日核销" },
      { value: "¥4,280", label: "预计入账" },
      { value: "92", label: "本月评分" }
    ]
  },
  methods: {
    goVerify() {
      wx.navigateTo({ url: "/pages/merchant/verify/verify" })
    }
  }
})

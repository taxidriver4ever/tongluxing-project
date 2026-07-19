const places = [
  { name: "四姑娘山景区", address: "阿坝藏族羌族自治州小金县" },
  { name: "映秀服务区", address: "都汶高速映秀段" },
  { name: "青城后山", address: "成都市都江堰市泰安古镇" },
  { name: "新都桥镇", address: "甘孜藏族自治州康定市" }
]

Page({
  data: { keyword: "", results: places },
  onInput(event: WechatMiniprogram.Input) {
    const keyword = event.detail.value.trim()
    this.setData({ keyword, results: keyword ? places.filter(item => item.name.indexOf(keyword) >= 0 || item.address.indexOf(keyword) >= 0) : places })
  },
  choose(event: WechatMiniprogram.TouchEvent) {
    const name = String(event.currentTarget.dataset.name || "")
    wx.setStorageSync("selected_location", name)
    wx.navigateBack({ fail: () => wx.reLaunch({ url: "/pages/index/index" }) })
  }
})

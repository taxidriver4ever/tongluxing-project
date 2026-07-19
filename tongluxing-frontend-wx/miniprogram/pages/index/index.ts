import { getNavigationLayout } from "../../utils/navigation-layout"

Page({
  data: {
    contentTop: 82,
    sheetVisible: false,
    sheetType: "",
    sheetTitle: "",
    layers: [{ name: "附近车队", active: true }, { name: "我的路线", active: true }, { name: "服务点 POI", active: false }],
    markers: [
      { id: 1, latitude: 30.657, longitude: 104.066, callout: { content: "我的位置", display: "ALWAYS", padding: 8, borderRadius: 8, bgColor: "#3A86FF", color: "#FFFFFF", fontSize: 12 } },
      { id: 2, latitude: 30.676, longitude: 104.041, callout: { content: "川西慢游车队", display: "ALWAYS", padding: 8, borderRadius: 8, bgColor: "#FFFFFF", color: "#111827", fontSize: 12 } }
    ]
  },
  onLoad() {
    this.setData({ contentTop: getNavigationLayout().contentTop })
  },
  goSearch() { wx.navigateTo({ url: "/pages/home/search/search" }) },
  locate() { wx.showToast({ title: "已回到当前位置", icon: "none" }) },
  openSheet(event: WechatMiniprogram.TouchEvent) {
    const sheetType = String(event.currentTarget.dataset.sheet || "")
    const titles: Record<string, string> = { trip: "发布行程", location: "当前位置：广州天河", layer: "地图显示", sos: "SOS紧急求助" }
    this.setData({ sheetVisible: true, sheetType, sheetTitle: titles[sheetType] || "" })
  },
  closeSheet() { this.setData({ sheetVisible: false }) },
  toggleLayer(event: WechatMiniprogram.TouchEvent) {
    const index = Number(event.currentTarget.dataset.index)
    const layers = this.data.layers.map((item, itemIndex) => itemIndex === index ? { ...item, active: !item.active } : item)
    this.setData({ layers })
  },
  goCreateTrip() { wx.navigateTo({ url: "/pages/trip/create/create" }) }
})

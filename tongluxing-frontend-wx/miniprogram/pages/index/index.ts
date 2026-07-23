import { getNavigationLayout } from "../../utils/navigation-layout"

Page({
  data: {
    contentTop: 82, latitude: 30.2741, longitude: 120.1551, trafficEnabled: false, selectedName: ""
  },
  onLoad() {
    this.setData({ contentTop: getNavigationLayout().contentTop })
    this.locate(false)
  },
  goSearch() { wx.navigateTo({ url: "/pages/home/search/search" }) },
  goCreateTrip() { wx.navigateTo({ url: "/pages/trip/create/create" }) },
  locate(showToast: boolean = true) {
    wx.getLocation({
      type: "gcj02",
      success: result => {
        this.setData({ latitude: result.latitude, longitude: result.longitude })
        if (showToast) wx.showToast({ title: "已回到当前位置", icon: "none" })
      },
      fail: () => wx.showToast({ title: "请允许定位权限", icon: "none" })
    })
  },
  toggleTraffic() {
    const trafficEnabled = !this.data.trafficEnabled
    this.setData({ trafficEnabled })
    wx.showToast({ title: trafficEnabled ? "实时路况已开启" : "实时路况已关闭", icon: "none" })
  },
  sos() { wx.navigateTo({ url: "/pages/sos/sos?latitude=" + this.data.latitude + "&longitude=" + this.data.longitude }) }
})

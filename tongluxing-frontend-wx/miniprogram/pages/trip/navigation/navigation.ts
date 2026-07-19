import { getNavigationLayout } from "../../../utils/navigation-layout"

Page({
  data: { contentTop: 82 },
  onLoad() { this.setData({ contentTop: getNavigationLayout().contentTop }) },
  waypoints() { wx.navigateTo({ url: "/pages/trip/route-waypoints/route-waypoints" }) },
  recenter() { wx.showToast({ title: "已回到当前位置", icon: "none" }) },
  exit() { wx.showModal({ title: "退出导航", content: "退出后仍可从行程首页继续导航", success: result => { if (result.confirm) wx.reLaunch({ url: "/pages/trip/trip" }) } }) }
})

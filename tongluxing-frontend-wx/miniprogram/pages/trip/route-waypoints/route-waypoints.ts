import { mockWaypoints } from "../../../data/mock-core"

Page({ data: { waypoints: mockWaypoints }, remove(event: WechatMiniprogram.TouchEvent) { const id = event.currentTarget.dataset.id; this.setData({ waypoints: this.data.waypoints.filter(item => item.id !== id) }) }, add() { wx.navigateTo({ url: "/pages/home/search/search" }) }, confirm() { wx.showToast({ title: "路线已更新", icon: "success" }); setTimeout(() => wx.navigateBack(), 400) } })

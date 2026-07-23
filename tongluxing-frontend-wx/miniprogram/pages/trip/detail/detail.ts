import { Trip, getTrip } from "../../../api/trip"
import { getTripConversation } from "../../../api/chat"
import { promptDownloadApp } from "../../../utils/app-download"

interface TripDetailView extends Trip {
  distanceText: string
  durationText: string
  statusText: string
  peopleText: string
}

Page({
  data: { safeTop: 44, loading: true, trip: null as TripDetailView | null },
  onLoad(options: Record<string, string>) {
    try { this.setData({ safeTop: wx.getSystemInfoSync().statusBarHeight || 44 }) } catch (_) {}
    this.load(options.tripId)
  },
  async load(tripId?: string) {
    if (!tripId) { wx.showToast({ title: "缺少行程编号", icon: "none" }); return }
    try {
      const trip = await getTrip(tripId)
      this.setData({ trip: { ...trip, distanceText: Math.round((trip.routeDistance || 0) / 1000) + " km",
        durationText: ((trip.routeDuration || 0) / 3600).toFixed(1) + " h",
        statusText: ({ PUBLISHED: "已发布", ONGOING: "进行中", ENDED: "已结束", CANCELLED: "已取消" } as Record<string, string>)[trip.status] || trip.status,
        peopleText: (trip.joinedVehicleCount || 1) + "/" + (trip.expectedPeople || trip.maxVehicleCount) }, loading: false })
    } catch (error) { wx.showToast({ title: error instanceof Error ? error.message : "行程加载失败", icon: "none" }); this.setData({ loading: false }) }
  },
  back() { wx.navigateBack({ fail: () => wx.reLaunch({ url: "/pages/trip/trip" }) }) },
  editRoute() { wx.navigateTo({ url: "/pages/trip/create/create" }) },
  members() { if (this.data.trip) wx.navigateTo({ url: "/pages/team/members/members?tripId=" + this.data.trip.tripId }) },
  async chat() {
    if (!this.data.trip) return
    try {
      const conversation = await getTripConversation(this.data.trip.tripId)
      wx.navigateTo({ url: "/pages/chat/session/session?id=" + conversation.conversationId })
    } catch (error) {
      wx.showToast({ title: error instanceof Error ? error.message : "该行程暂未创建群聊", icon: "none" })
    }
  },
  start() { promptDownloadApp("开启行程和实时导航") }
})

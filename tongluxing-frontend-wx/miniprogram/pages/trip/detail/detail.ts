import { Trip, getTrip } from "../../../api/trip"

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
  members() { wx.navigateTo({ url: "/pages/team/members/members" }) },
  chat() { wx.navigateTo({ url: "/pages/chat/session/session?id=CHAT01" }) },
  start() { if (this.data.trip) wx.navigateTo({ url: "/pages/trip/start/start?tripId=" + this.data.trip.tripId }) }
})

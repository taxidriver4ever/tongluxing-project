import { CoreTrip, getMockTrips } from "../../../data/mock-core"

Page({
  data: { active: "all", filters: [{ key: "all", label: "全部" }, { key: "active", label: "进行中" }, { key: "upcoming", label: "待出发" }], trips: [] as CoreTrip[], visibleTrips: [] as CoreTrip[] },
  onLoad() { getMockTrips().then(trips => this.setData({ trips, visibleTrips: trips })) },
  changeFilter(event: WechatMiniprogram.TouchEvent) { const active = String(event.currentTarget.dataset.key); this.setData({ active, visibleTrips: active === "all" ? this.data.trips : this.data.trips.filter(item => item.status === active) }) },
  goDetail(event: WechatMiniprogram.TouchEvent) { wx.navigateTo({ url: "/pages/trip/detail/detail?tripId=" + event.currentTarget.dataset.id }) }
})

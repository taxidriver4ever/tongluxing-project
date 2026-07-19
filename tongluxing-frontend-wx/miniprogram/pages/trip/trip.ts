import { CoreTrip, getMockTrips } from "../../data/mock-core"
import { getNavigationLayout } from "../../utils/navigation-layout"

Page({
  data: { contentTop: 82, currentTrip: null as CoreTrip | null, trips: [] as CoreTrip[] },
  onLoad() { this.setData({ contentTop: getNavigationLayout().contentTop }) },
  onShow() { getMockTrips().then(items => this.setData({ currentTrip: items[0], trips: items.slice(1) })) },
  goDetail(event: WechatMiniprogram.TouchEvent) { wx.navigateTo({ url: "/pages/trip/detail/detail?tripId=" + event.currentTarget.dataset.id }) },
  goStart() { wx.navigateTo({ url: "/pages/trip/start/start?tripId=" + (this.data.currentTrip ? this.data.currentTrip.id : "T1001") }) },
  goList() { wx.navigateTo({ url: "/pages/trip/list/list" }) },
  goCreate() { wx.navigateTo({ url: "/pages/trip/create/create" }) }
})

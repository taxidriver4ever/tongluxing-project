import { MatchTripCard, getNearbyTrips } from "../../api/match"
import { Trip, getMyTrips } from "../../api/trip"
import { promptDownloadApp } from "../../utils/app-download"
import { getNavigationLayout } from "../../utils/navigation-layout"

interface NearbyView extends MatchTripCard { title: string }
Page({
  data: { contentTop: 82, loading: true, currentTrip: null as Trip | null, nearby: [] as NearbyView[] },
  onLoad() { this.setData({ contentTop: getNavigationLayout().contentTop }) },
  onShow() { void this.load() },
  async load() {
    this.setData({ loading: true })
    try {
      const mine = await getMyTrips("active"); const currentTrip = mine.trips.find(item => item.status === "RUNNING") || mine.trips[0] || null
      this.setData({ currentTrip })
    } catch (_error) { this.setData({ currentTrip: null }) }
    try {
      const location = await new Promise<{ latitude: number; longitude: number }>(resolve => wx.getLocation({ type:"gcj02", success:result => resolve({ latitude: result.latitude, longitude: result.longitude }), fail:() => resolve({ latitude:30.2741, longitude:120.1551 }) }))
      const result = await getNearbyTrips(location.latitude, location.longitude, 50000, 6)
      this.setData({ nearby: result.trips.map(item => ({ ...item, title: item.startName + " → " + item.endName })) })
    } catch (_error) { this.setData({ nearby: [] }) }
    this.setData({ loading: false })
  },
  goCreate() { wx.navigateTo({ url: "/pages/trip/create/create" }) },
  goOverview() { wx.navigateTo({ url: "/pages/trip/list/list?scope=active" }) },
  goHistory() { wx.navigateTo({ url: "/pages/trip/history/history" }) },
  goDrafts() { wx.navigateTo({ url: "/pages/trip/list/list?drafts=1" }) },
  goDiscovery() { wx.navigateTo({ url: "/pages/trip/public-list/public-list" }) },
  goNearby() { wx.navigateTo({ url: "/pages/trip/public-list/public-list" }) },
  goCurrent() { const row=this.data.currentTrip; if(row) wx.navigateTo({url:"/pages/trip/detail/detail?tripId="+row.tripId}); else this.goCreate() },
  goNearbyDetail(event: WechatMiniprogram.TouchEvent) { wx.navigateTo({ url: "/pages/trip/detail/detail?tripId=" + event.currentTarget.dataset.id }) },
  downloadApp() { promptDownloadApp("开启行程、导航、轨迹记录和行程结束") }
})

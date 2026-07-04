import { getNearbyMap, MapMarker } from "../../api/map"
import { getNearbyTeams, MatchTeamCard } from "../../api/match"

Component({
  data: {
    loading: false,
    markers: [] as MapMarker[],
    teams: [] as MatchTeamCard[],
    latitude: 23.1291,
    longitude: 113.2644
  },
  lifetimes: {
    attached() {
      this.loadNearby()
    }
  },
  methods: {
    async loadNearby() {
      this.setData({ loading: true })
      try {
        const nearby = await getNearbyMap(this.data.latitude, this.data.longitude, 5000)
        const teams = await getNearbyTeams(this.data.latitude, this.data.longitude, 50000, 3)
        this.setData({
          markers: nearby.markers || [],
          teams: teams.teams || []
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    goPublishTrip() {
      wx.navigateTo({
        url: "/pages/publish-trip/publish-trip"
      })
    },
    goNearbyTeams() {
      wx.navigateTo({ url: "/pages/match/nearby-teams/nearby-teams" })
    },
    goTeamDetail(event: WechatMiniprogram.TouchEvent) {
      const teamId = String(event.currentTarget.dataset.id || "")
      if (teamId) {
        wx.navigateTo({ url: "/pages/team/detail/detail?teamId=" + teamId })
      }
    },
    goSos() {
      wx.navigateTo({
        url: "/pages/sos/sos"
      })
    },
    goGroupbuyList() {
      wx.navigateTo({
        url: "/pages/groupbuy/list/list"
      })
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

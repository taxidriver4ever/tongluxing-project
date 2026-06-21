import { endTrip, getMyTrips, getPublicTrips, Trip } from "../../api/trip"
import { getTripRecommendations, MatchTeamCard } from "../../api/match"

Component({
  data: {
    loading: false,
    currentTrip: null as Trip | null,
    publicTrips: [] as Trip[],
    recommendedTeams: [] as MatchTeamCard[]
  },
  pageLifetimes: {
    show() {
      this.loadTrips()
    }
  },
  methods: {
    async loadTrips() {
      this.setData({ loading: true })
      try {
        const mine = await getMyTrips("active")
        const publicResult = await getPublicTrips(10)
        const currentTrip = mine.trips && mine.trips.length ? mine.trips[0] : null
        let recommendedTeams: MatchTeamCard[] = []
        if (currentTrip && currentTrip.tripId) {
          const recommendation = await getTripRecommendations(currentTrip.tripId, 5)
          recommendedTeams = recommendation.teams || []
        }
        this.setData({
          currentTrip,
          publicTrips: publicResult.trips || [],
          recommendedTeams
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    goPublish() {
      wx.navigateTo({ url: "/pages/publish-trip/publish-trip" })
    },
    goHistory() {
      wx.navigateTo({ url: "/pages/trip/history/history" })
    },
    goPublicList() {
      wx.navigateTo({ url: "/pages/trip/public-list/public-list" })
    },
    goNearbyTeams() {
      wx.navigateTo({ url: "/pages/match/nearby-teams/nearby-teams" })
    },
    goCreateTeam() {
      wx.navigateTo({ url: "/pages/team/create/create" })
    },
    goTeamDetail(event: WechatMiniprogram.TouchEvent) {
      const teamId = String(event.currentTarget.dataset.id || "")
      if (teamId) {
        wx.navigateTo({ url: "/pages/team/detail/detail?teamId=" + teamId })
      }
    },
    applyTeam(event: WechatMiniprogram.TouchEvent) {
      const teamId = String(event.currentTarget.dataset.id || "")
      if (teamId) {
        wx.navigateTo({ url: "/pages/team/apply/apply?teamId=" + teamId })
      }
    },
    goDetail(event: WechatMiniprogram.TouchEvent) {
      const tripId = String(event.currentTarget.dataset.id || "")
      if (tripId) {
        wx.navigateTo({ url: "/pages/trip/detail/detail?tripId=" + tripId })
      }
    },
    goEdit() {
      const trip = this.data.currentTrip
      if (trip && trip.tripId) {
        wx.navigateTo({ url: "/pages/publish-trip/publish-trip?tripId=" + trip.tripId })
      }
    },
    onEndTrip() {
      const trip = this.data.currentTrip
      if (!trip || !trip.tripId) {
        return
      }
      wx.showModal({
        title: "结束行程",
        content: "结束后行程会进入历史记录，确定继续吗？",
        confirmText: "结束",
        success: async (result) => {
          if (!result.confirm) {
            return
          }
          try {
            await endTrip(trip.tripId)
            wx.showToast({ title: "行程已结束", icon: "success" })
            this.loadTrips()
          } catch (error) {
            wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
          }
        }
      })
    },
    goGroupbuyList() {
      wx.navigateTo({ url: "/pages/groupbuy/list/list" })
    },
    depthText(depth: string): string {
      if (depth === "LIGHT") {
        return "浅"
      }
      if (depth === "DEEP") {
        return "深"
      }
      return "中"
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

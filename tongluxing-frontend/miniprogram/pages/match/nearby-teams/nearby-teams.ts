import { getNearbyTeams, MatchTeamCard } from "../../../api/match"

Component({
  data: {
    loading: false,
    teams: [] as MatchTeamCard[],
    latitude: 23.1291,
    longitude: 113.2644
  },
  lifetimes: {
    attached() {
      this.loadTeams()
    }
  },
  methods: {
    async loadTeams() {
      this.setData({ loading: true })
      try {
        const result = await getNearbyTeams(this.data.latitude, this.data.longitude, 50000, 20)
        this.setData({ teams: result.teams || [] })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    goDetail(event: WechatMiniprogram.TouchEvent) {
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
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

import { getPublicTrips, Trip } from "../../../api/trip"

Component({
  data: {
    loading: false,
    trips: [] as Trip[]
  },
  lifetimes: {
    attached() {
      this.loadTrips()
    }
  },
  methods: {
    async loadTrips() {
      this.setData({ loading: true })
      try {
        const result = await getPublicTrips(50)
        this.setData({ trips: result.trips || [] })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    goDetail(event: WechatMiniprogram.TouchEvent) {
      const tripId = String(event.currentTarget.dataset.id || "")
      if (tripId) {
        wx.navigateTo({ url: "/pages/trip/detail/detail?tripId=" + tripId })
      }
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

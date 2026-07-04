import { cancelTrip, endTrip, getTrip, getTripMembers, Trip, TripMember } from "../../../api/trip"

function emptyTrip(): Trip {
  return {
    tripId: "",
    userId: "",
    vehicleId: "",
    startName: "",
    startLocation: {
      name: "",
      address: "",
      latitude: 0,
      longitude: 0
    },
    endName: "",
    endLocation: {
      name: "",
      address: "",
      latitude: 0,
      longitude: 0
    },
    routeSummary: "",
    routePolylineKey: "",
    routeDistance: 0,
    routeDuration: 0,
    routePolyline: "",
    departureTime: "",
    estimatedDays: 1,
    totalDistanceMeters: 0,
    maxVehicleCount: 1,
    joinedVehicleCount: 1,
    travelDepth: "MIDDLE",
    publicFlag: true,
    status: "",
    remark: "",
    waypoints: [],
    createdAt: "",
    updatedAt: ""
  }
}

Component({
  data: {
    loading: false,
    tripId: "",
    trip: emptyTrip(),
    members: [] as TripMember[]
  },
  lifetimes: {
    attached() {
      this.loadFromPageOptions()
    }
  },
  methods: {
    onLoad(options: Record<string, string>) {
      this.initByOptions(options)
    },
    loadFromPageOptions() {
      const pages = getCurrentPages()
      const current = pages.length ? pages[pages.length - 1] as any : null
      const options = current && current.options ? current.options : {}
      if (options.tripId) {
        this.initByOptions(options)
      }
    },
    initByOptions(options: Record<string, string>) {
      const tripId = String(options.tripId || "")
      if (!tripId || this.data.tripId) {
        return
      }
      this.setData({ tripId })
      this.loadDetail(tripId)
    },
    async loadDetail(tripId: string) {
      this.setData({ loading: true })
      try {
        const trip = await getTrip(tripId)
        const members = await getTripMembers(tripId)
        this.setData({ trip, members })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    goEdit() {
      wx.navigateTo({ url: "/pages/publish-trip/publish-trip?tripId=" + this.data.tripId })
    },
    goCreateTeam() {
      wx.navigateTo({ url: "/pages/team/create/create" })
    },
    onEnd() {
      this.confirmStatus("结束行程", "结束后会进入历史行程。", "结束", async () => {
        await endTrip(this.data.tripId)
      })
    },
    onCancel() {
      this.confirmStatus("取消行程", "取消后会进入历史行程。", "取消", async () => {
        await cancelTrip(this.data.tripId)
      })
    },
    confirmStatus(title: string, content: string, confirmText: string, action: () => Promise<void>) {
      wx.showModal({
        title,
        content,
        confirmText,
        success: async (result) => {
          if (!result.confirm) {
            return
          }
          try {
            await action()
            wx.showToast({ title: "操作成功", icon: "success" })
            setTimeout(() => {
              wx.navigateBack()
            }, 500)
          } catch (error) {
            wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
          }
        }
      })
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

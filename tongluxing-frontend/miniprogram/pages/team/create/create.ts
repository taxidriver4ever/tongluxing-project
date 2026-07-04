import { getMyTrips, Trip } from "../../../api/trip"
import { createTeam } from "../../../api/team"

Component({
  data: {
    loading: false,
    trips: [] as Trip[],
    selectedIndex: 0,
    selectedTripText: "暂无可用行程",
    form: {
      teamName: "",
      teamDesc: "",
      maxMemberCount: 4,
      joinMode: "APPROVAL",
      publicFlag: true,
      notice: ""
    }
  },
  lifetimes: {
    attached() {
      this.loadTrips()
    }
  },
  methods: {
    async loadTrips() {
      try {
        const result = await getMyTrips("active")
        const trips = result.trips || []
        this.setData({
          trips,
          selectedTripText: this.tripText(trips[0])
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      }
    },
    onTripChange(event: any) {
      const selectedIndex = Number(event.detail.value || 0)
      this.setData({
        selectedIndex,
        selectedTripText: this.tripText(this.data.trips[selectedIndex])
      })
    },
    onInput(event: any) {
      const field = event.currentTarget.dataset.field
      if (!field) {
        return
      }
      this.setData({ ["form." + field]: event.detail.value })
    },
    onNumberInput(event: any) {
      const field = event.currentTarget.dataset.field
      if (!field) {
        return
      }
      this.setData({ ["form." + field]: Number(event.detail.value || 0) })
    },
    onPublicChange(event: any) {
      this.setData({ "form.publicFlag": event.detail.value })
    },
    async submit() {
      const trips = this.data.trips
      const trip = trips[this.data.selectedIndex]
      const form = this.data.form
      if (!trip) {
        wx.showToast({ title: "请先发布行程", icon: "none" })
        return
      }
      if (!form.teamName) {
        wx.showToast({ title: "请填写车队名", icon: "none" })
        return
      }
      this.setData({ loading: true })
      try {
        const team = await createTeam({
          tripId: Number(trip.tripId),
          ownerVehicleId: Number(trip.vehicleId),
          teamName: form.teamName,
          teamDesc: form.teamDesc,
          maxMemberCount: Number(form.maxMemberCount || 4),
          joinMode: form.joinMode,
          publicFlag: form.publicFlag,
          notice: form.notice
        })
        wx.showToast({ title: "车队已创建", icon: "success" })
        setTimeout(() => {
          wx.redirectTo({ url: "/pages/team/detail/detail?teamId=" + team.teamId })
        }, 500)
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    },
    tripText(trip: Trip | undefined): string {
      if (!trip) {
        return "暂无可用行程"
      }
      return trip.startName + " → " + trip.endName
    }
  }
})

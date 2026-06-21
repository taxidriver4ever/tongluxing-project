import { createTrip, getTrip, TripLocation, TripWaypoint, updateTrip } from "../../api/trip"
import { getMyVehicles } from "../../api/vehicle"
import { planRoute } from "../../api/map"

function emptyLocation(): TripLocation {
  return {
    name: "",
    address: "",
    latitude: 0,
    longitude: 0
  }
}

function locationText(location: TripLocation): string {
  if (location.name) {
    return location.name
  }
  if (location.address) {
    return location.address
  }
  return "未选择"
}

Component({
  data: {
    loading: false,
    tripId: "",
    isEdit: false,
    form: {
      vehicleId: 0,
      startLocation: emptyLocation(),
      endLocation: emptyLocation(),
      waypoints: [] as TripWaypoint[],
      routeSummary: "",
      routeDistance: 0,
      routeDuration: 0,
      routePolyline: "",
      departureTime: "2026-06-20 08:00:00",
      estimatedDays: 3,
      maxVehicleCount: 4,
      travelDepth: "MIDDLE",
      publicFlag: true,
      remark: ""
    },
    startText: "请选择起点",
    endText: "请选择终点"
  },
  lifetimes: {
    attached() {
      this.loadDefaultVehicle()
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
      this.setData({ tripId, isEdit: true })
      this.loadTrip(tripId)
    },
    async loadDefaultVehicle() {
      try {
        const result = await getMyVehicles()
        const vehicles = result.vehicles || []
        if (vehicles.length) {
          this.setData({ "form.vehicleId": Number(vehicles[0].vehicleId) })
        }
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      }
    },
    async loadTrip(tripId: string) {
      this.setData({ loading: true })
      try {
        const trip = await getTrip(tripId)
        const startLocation = trip.startLocation || emptyLocation()
        const endLocation = trip.endLocation || emptyLocation()
        this.setData({
          form: {
            vehicleId: Number(trip.vehicleId),
            startLocation,
            endLocation,
            waypoints: trip.waypoints || [],
            routeSummary: trip.routeSummary || "",
            routeDistance: trip.routeDistance || 0,
            routeDuration: trip.routeDuration || 0,
            routePolyline: trip.routePolyline || "",
            departureTime: trip.departureTime || "",
            estimatedDays: trip.estimatedDays || 1,
            maxVehicleCount: trip.maxVehicleCount || 4,
            travelDepth: trip.travelDepth || "MIDDLE",
            publicFlag: trip.publicFlag,
            remark: trip.remark || ""
          },
          startText: locationText(startLocation),
          endText: locationText(endLocation)
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    chooseStartLocation() {
      this.chooseLocation((location) => {
        this.setData({
          "form.startLocation": location,
          startText: locationText(location)
        })
        this.refreshRoutePlan()
      })
    },
    chooseEndLocation() {
      this.chooseLocation((location) => {
        this.setData({
          "form.endLocation": location,
          endText: locationText(location)
        })
        this.refreshRoutePlan()
      })
    },
    addWaypoint() {
      const waypoints = this.data.form.waypoints
      if (waypoints.length >= 5) {
        wx.showToast({ title: "途经点最多 5 个", icon: "none" })
        return
      }
      this.chooseLocation((location) => {
        const next = waypoints.concat([{
          name: location.name,
          address: location.address,
          latitude: location.latitude,
          longitude: location.longitude,
          sortOrder: waypoints.length + 1
        }])
        this.setWaypoints(next)
      })
    },
    editWaypoint(event: WechatMiniprogram.TouchEvent) {
      const index = Number(event.currentTarget.dataset.index || 0)
      const waypoints = this.data.form.waypoints
      if (index < 0 || index >= waypoints.length) {
        return
      }
      this.chooseLocation((location) => {
        waypoints[index] = {
          name: location.name,
          address: location.address,
          latitude: location.latitude,
          longitude: location.longitude,
          sortOrder: index + 1
        }
        this.setWaypoints(waypoints)
      })
    },
    deleteWaypoint(event: WechatMiniprogram.TouchEvent) {
      const index = Number(event.currentTarget.dataset.index || 0)
      const waypoints = this.data.form.waypoints.filter((_, itemIndex) => itemIndex !== index)
      this.setWaypoints(waypoints)
    },
    moveWaypointUp(event: WechatMiniprogram.TouchEvent) {
      const index = Number(event.currentTarget.dataset.index || 0)
      if (index <= 0) {
        return
      }
      const waypoints = this.data.form.waypoints
      const temp = waypoints[index - 1]
      waypoints[index - 1] = waypoints[index]
      waypoints[index] = temp
      this.setWaypoints(waypoints)
    },
    moveWaypointDown(event: WechatMiniprogram.TouchEvent) {
      const index = Number(event.currentTarget.dataset.index || 0)
      const waypoints = this.data.form.waypoints
      if (index >= waypoints.length - 1) {
        return
      }
      const temp = waypoints[index + 1]
      waypoints[index + 1] = waypoints[index]
      waypoints[index] = temp
      this.setWaypoints(waypoints)
    },
    setWaypoints(waypoints: TripWaypoint[]) {
      const sorted = waypoints.map((item, index) => {
        return {
          name: item.name,
          address: item.address,
          latitude: item.latitude,
          longitude: item.longitude,
          sortOrder: index + 1
        }
      })
      this.setData({ "form.waypoints": sorted })
      this.refreshRoutePlan()
    },
    chooseLocation(callback: (location: TripLocation) => void) {
      wx.chooseLocation({
        success(result) {
          callback({
            name: result.name || "",
            address: result.address || "",
            latitude: result.latitude,
            longitude: result.longitude
          })
        },
        fail(error) {
          wx.showToast({ title: error.errMsg || "地图选点失败", icon: "none" })
        }
      })
    },
    refreshRoutePlan() {
      const form = this.data.form
      const routePoints = [form.startLocation].concat(form.waypoints).concat([form.endLocation])
      const names = routePoints
        .filter((item) => item.latitude && item.longitude)
        .map((item) => locationText(item))
      this.setData({
        "form.routeSummary": names.join(" → "),
        "form.routePolyline": JSON.stringify(routePoints.filter((item) => item.latitude && item.longitude)),
        "form.routeDistance": 0,
        "form.routeDuration": 0
      })
    },
    onInput(event: any) {
      const field = event.currentTarget.dataset.field
      if (!field) {
        return
      }
      this.setData({
        ["form." + field]: event.detail.value
      })
    },
    onNumberInput(event: any) {
      const field = event.currentTarget.dataset.field
      if (!field) {
        return
      }
      this.setData({
        ["form." + field]: Number(event.detail.value || 0)
      })
    },
    onDepthTap(event: WechatMiniprogram.TouchEvent) {
      this.setData({ "form.travelDepth": String(event.currentTarget.dataset.depth || "MIDDLE") })
    },
    onPublicChange(event: any) {
      this.setData({ "form.publicFlag": event.detail.value })
    },
    async onSubmit() {
      const form = this.data.form
      if (!form.vehicleId) {
        wx.showToast({ title: "请先添加并认证车辆", icon: "none" })
        return
      }
      if (!form.startLocation.latitude || !form.startLocation.longitude || !form.endLocation.latitude || !form.endLocation.longitude) {
        wx.showToast({ title: "请通过地图选择起点和终点", icon: "none" })
        return
      }
      if (!form.departureTime) {
        wx.showToast({ title: "请填写出发时间", icon: "none" })
        return
      }
      this.refreshRoutePlan()
      this.setData({ loading: true })
      try {
        const route = await planRoute({
          startLocation: form.startLocation,
          endLocation: form.endLocation,
          waypoints: form.waypoints.map((item) => {
            return {
              name: item.name,
              address: item.address,
              latitude: item.latitude,
              longitude: item.longitude
            }
          })
        })
        this.setData({
          "form.routeDistance": route.routeDistance || 0,
          "form.routeDuration": route.routeDuration || 0,
          "form.routePolyline": route.routePolyline || form.routePolyline
        })
        const data = {
          vehicleId: form.vehicleId,
          startLocation: form.startLocation,
          endLocation: form.endLocation,
          waypoints: form.waypoints,
          routeSummary: form.routeSummary,
          routeDistance: Number(route.routeDistance || 0),
          routeDuration: Number(route.routeDuration || 0),
          routePolyline: route.routePolyline || form.routePolyline,
          departureTime: form.departureTime,
          estimatedDays: Number(form.estimatedDays || 1),
          maxVehicleCount: Number(form.maxVehicleCount || 4),
          travelDepth: form.travelDepth,
          publicFlag: form.publicFlag,
          remark: form.remark
        }
        const trip = this.data.isEdit ? await updateTrip(this.data.tripId, data) : await createTrip(data)
        wx.showToast({ title: this.data.isEdit ? "行程已保存" : "行程已发布", icon: "success" })
        setTimeout(() => {
          wx.redirectTo({ url: "/pages/trip/detail/detail?tripId=" + trip.tripId })
        }, 500)
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    goBack() {
      wx.navigateBack()
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

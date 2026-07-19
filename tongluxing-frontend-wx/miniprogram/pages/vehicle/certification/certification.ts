import { getMyVehicles, getVehicleAuthStatus, submitVehicleAuth } from "../../../api/vehicle"

Component({
  data: {
    loading: false,
    status: "UNSUBMITTED",
    reason: "",
    submitTime: "",
    form: {
      vehicleBrand: "",
      vehicleModel: "",
      plateNumber: "",
      vehicleColor: ""
    },
    driverLicenseImages: ["", ""],
    registrationLicenseImages: ["", ""],
    vehicleImages: ["", "", ""]
  },
  lifetimes: {
    attached() {
      this.loadPage()
    }
  },
  methods: {
    async loadPage() {
      this.setData({ loading: true })
      try {
        const [status, vehicles] = await Promise.all([getVehicleAuthStatus(), getMyVehicles()])
        const vehicle = vehicles.vehicles && vehicles.vehicles.length ? vehicles.vehicles[0] : null
        this.setData({
          status: status.status || "UNSUBMITTED",
          reason: status.reason || "",
          submitTime: status.submitTime || "",
          "form.vehicleBrand": vehicle ? vehicle.brand : "",
          "form.vehicleModel": vehicle ? vehicle.model : "",
          // 后端列表只返回脱敏车牌，拒绝后重新提交时不能把掩码当成真实车牌。
          "form.plateNumber": "",
          "form.vehicleColor": vehicle ? vehicle.color : ""
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    onInput(event: any) {
      const field = event.currentTarget.dataset.field
      if (field) this.setData({ ["form." + field]: event.detail.value })
    },
    chooseImage(event: any) {
      if (this.data.status === "PENDING" || this.data.status === "PASS") return
      const group = event.currentTarget.dataset.group
      const index = Number(event.currentTarget.dataset.index)
      wx.chooseMedia({
        count: 1,
        mediaType: ["image"],
        sourceType: ["album", "camera"],
        success: (result) => {
          const file = result.tempFiles && result.tempFiles[0]
          if (!file) return
          this.setData({ [`${group}[${index}]`]: file.tempFilePath })
        }
      })
    },
    async onSubmit() {
      if (this.data.loading) return
      if (this.data.status === "PENDING") {
        wx.showToast({ title: "车辆认证审核中", icon: "none" })
        return
      }
      if (this.data.status === "PASS") {
        wx.showToast({ title: "车辆已经认证", icon: "none" })
        return
      }
      const form = this.data.form
      const drivers = this.data.driverLicenseImages.filter(Boolean)
      const registrations = this.data.registrationLicenseImages.filter(Boolean)
      const vehicles = this.data.vehicleImages.filter(Boolean)
      if (!form.vehicleBrand || !form.vehicleModel || !form.plateNumber || !form.vehicleColor) {
        wx.showToast({ title: "请完善车辆基础信息", icon: "none" })
        return
      }
      if (drivers.length !== 2 || registrations.length !== 2 || vehicles.length < 1) {
        wx.showToast({ title: "请上传全部必填图片", icon: "none" })
        return
      }
      this.setData({ loading: true })
      try {
        const result = await submitVehicleAuth({
          ...form,
          driverLicenseImages: drivers,
          registrationLicenseImages: registrations,
          vehicleImages: vehicles
        })
        this.setData({ status: result.status, reason: result.reason || "", submitTime: result.submitTime || "" })
        wx.showToast({ title: "认证申请已提交", icon: "success" })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

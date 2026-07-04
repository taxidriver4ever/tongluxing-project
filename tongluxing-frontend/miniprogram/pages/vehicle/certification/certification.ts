import { getVehicle, getVehicleCertification, submitVehicleCertification, Vehicle } from "../../../api/vehicle"

function defaultVehicle(): Vehicle {
  return {
    vehicleId: 0,
    userId: 0,
    plateNoMask: "",
    brand: "",
    model: "",
    vehicleType: "",
    color: "",
    seatCount: 5,
    energyType: "",
    vehiclePhotoImageKey: "",
    certificationStatus: "UNSUBMITTED",
    isDefault: false
  }
}

Component({
  data: {
    loading: false,
    vehicleId: 0,
    vehicle: defaultVehicle(),
    status: "UNSUBMITTED",
    rejectReason: "",
    submittedAt: "",
    form: {
      ownerName: "",
      plateNo: "",
      vin: "",
      engineNo: "",
      licenseImageKey: ""
    }
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
      if (options.vehicleId) {
        this.initByOptions(options)
      }
    },
    initByOptions(options: Record<string, string>) {
      if (this.data.vehicleId) {
        return
      }
      const vehicleId = Number(options.vehicleId || 0)
      if (!vehicleId) {
        wx.showToast({ title: "缺少车辆信息", icon: "none" })
        return
      }
      this.setData({ vehicleId })
      this.loadPage(vehicleId)
    },
    async loadPage(vehicleId: number) {
      this.setData({ loading: true })
      try {
        const vehicle = await getVehicle(vehicleId)
        const cert = await getVehicleCertification(vehicleId)
        this.setData({
          vehicle,
          status: cert.status || "UNSUBMITTED",
          rejectReason: cert.rejectReason || "",
          submittedAt: cert.submittedAt || "",
          "form.ownerName": cert.ownerName || "",
          "form.plateNo": cert.plateNoMask || vehicle.plateNoMask || "",
          "form.vin": cert.vinMask || "",
          "form.engineNo": cert.engineNoMask || "",
          "form.licenseImageKey": cert.licenseImageKey || ""
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
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
    chooseLicenseImage() {
      wx.chooseMedia({
        count: 1,
        mediaType: ["image"],
        sourceType: ["album", "camera"],
        success: (result) => {
          const file = result.tempFiles && result.tempFiles.length ? result.tempFiles[0] : null
          if (!file) {
            return
          }
          this.setData({
            "form.licenseImageKey": file.tempFilePath
          })
        }
      })
    },
    async onSubmit() {
      if (this.data.status === "PENDING") {
        wx.showToast({ title: "车辆认证审核中", icon: "none" })
        return
      }
      if (this.data.status === "APPROVED") {
        wx.showToast({ title: "车辆已认证", icon: "none" })
        return
      }
      const form = this.data.form
      if (!form.ownerName || !form.plateNo || !form.licenseImageKey) {
        wx.showToast({ title: "请填写所有人、车牌和行驶证照片", icon: "none" })
        return
      }

      this.setData({ loading: true })
      try {
        const result = await submitVehicleCertification(this.data.vehicleId, {
          ownerName: form.ownerName,
          plateNo: form.plateNo,
          vin: form.vin,
          engineNo: form.engineNo,
          licenseImageKey: form.licenseImageKey
        })
        this.setData({
          status: result.status,
          rejectReason: result.rejectReason || "",
          submittedAt: result.submittedAt || ""
        })
        wx.showToast({ title: "车辆认证已提交", icon: "success" })
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

import { createVehicle, getVehicle, updateVehicle } from "../../../api/vehicle"

Component({
  data: {
    loading: false,
    vehicleId: 0,
    isEdit: false,
    form: {
      plateNo: "",
      brand: "",
      model: "",
      vehicleType: "",
      color: "",
      seatCount: 5,
      energyType: "",
      vehiclePhotoImageKey: ""
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
      if (vehicleId) {
        this.setData({
          vehicleId,
          isEdit: true
        })
        this.loadVehicle(vehicleId)
      }
    },
    async loadVehicle(vehicleId: number) {
      this.setData({ loading: true })
      try {
        const vehicle = await getVehicle(vehicleId)
        this.setData({
          form: {
            plateNo: vehicle.plateNoMask || "",
            brand: vehicle.brand || "",
            model: vehicle.model || "",
            vehicleType: vehicle.vehicleType || "",
            color: vehicle.color || "",
            seatCount: vehicle.seatCount || 5,
            energyType: vehicle.energyType || "",
            vehiclePhotoImageKey: vehicle.vehiclePhotoImageKey || ""
          }
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
    onSeatInput(event: any) {
      const value = Number(event.detail.value || 0)
      this.setData({
        "form.seatCount": value
      })
    },
    chooseVehicleImage() {
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
            "form.vehiclePhotoImageKey": file.tempFilePath
          })
        }
      })
    },
    async onSave() {
      const form = this.data.form
      if (!form.brand || !form.model) {
        wx.showToast({ title: "请填写品牌和车型", icon: "none" })
        return
      }
      if (!this.data.isEdit && !form.plateNo) {
        wx.showToast({ title: "请填写车牌号", icon: "none" })
        return
      }
      if (!form.seatCount || form.seatCount < 1) {
        wx.showToast({ title: "请填写座位数", icon: "none" })
        return
      }

      this.setData({ loading: true })
      try {
        const data = {
          plateNo: form.plateNo,
          brand: form.brand,
          model: form.model,
          vehicleType: form.vehicleType,
          color: form.color,
          seatCount: Number(form.seatCount),
          energyType: form.energyType,
          vehiclePhotoImageKey: form.vehiclePhotoImageKey
        }
        if (this.data.isEdit) {
          await updateVehicle(this.data.vehicleId, data)
        } else {
          await createVehicle(data)
        }
        wx.showToast({ title: "车辆信息已保存", icon: "success" })
        setTimeout(() => {
          wx.navigateBack()
        }, 500)
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

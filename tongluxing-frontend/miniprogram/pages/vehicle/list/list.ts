import { deleteVehicle, getMyVehicles, setDefaultVehicle, Vehicle } from "../../../api/vehicle"

Component({
  data: {
    loading: false,
    vehicles: [] as Vehicle[]
  },
  lifetimes: {
    attached() {
      this.loadVehicles()
    }
  },
  pageLifetimes: {
    show() {
      this.loadVehicles()
    }
  },
  methods: {
    async loadVehicles() {
      this.setData({ loading: true })
      try {
        const result = await getMyVehicles()
        this.setData({ vehicles: result.vehicles || [] })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    goCreate() {
      wx.navigateTo({ url: "/pages/vehicle/edit/edit" })
    },
    goEdit(event: WechatMiniprogram.TouchEvent) {
      const vehicleId = Number(event.currentTarget.dataset.id || 0)
      if (vehicleId) {
        wx.navigateTo({ url: "/pages/vehicle/edit/edit?vehicleId=" + vehicleId })
      }
    },
    goCertification(event: WechatMiniprogram.TouchEvent) {
      const vehicleId = Number(event.currentTarget.dataset.id || 0)
      if (vehicleId) {
        wx.navigateTo({ url: "/pages/vehicle/certification/certification?vehicleId=" + vehicleId })
      }
    },
    goPublicCard(event: WechatMiniprogram.TouchEvent) {
      const vehicleId = Number(event.currentTarget.dataset.id || 0)
      if (vehicleId) {
        wx.navigateTo({ url: "/pages/vehicle/public-card/public-card?vehicleId=" + vehicleId })
      }
    },
    async onSetDefault(event: WechatMiniprogram.TouchEvent) {
      const vehicleId = Number(event.currentTarget.dataset.id || 0)
      if (!vehicleId) {
        return
      }
      this.setData({ loading: true })
      try {
        await setDefaultVehicle(vehicleId)
        wx.showToast({ title: "已设为默认车辆", icon: "success" })
        await this.loadVehicles()
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    onDelete(event: WechatMiniprogram.TouchEvent) {
      const vehicleId = Number(event.currentTarget.dataset.id || 0)
      if (!vehicleId) {
        return
      }
      wx.showModal({
        title: "删除车辆",
        content: "删除后车辆资料和默认标记将不可用，确定继续吗？",
        confirmText: "删除",
        confirmColor: "#EF4444",
        success: async (result) => {
          if (!result.confirm) {
            return
          }
          this.setData({ loading: true })
          try {
            await deleteVehicle(vehicleId)
            wx.showToast({ title: "车辆已删除", icon: "success" })
            await this.loadVehicles()
          } catch (error) {
            wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
          } finally {
            this.setData({ loading: false })
          }
        }
      })
    },
    statusText(status: string): string {
      if (status === "APPROVED") {
        return "已认证"
      }
      if (status === "PENDING") {
        return "审核中"
      }
      if (status === "REJECTED") {
        return "认证被拒"
      }
      return "未认证"
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

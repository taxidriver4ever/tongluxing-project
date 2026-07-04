import { getPublicVehicleCard, PublicVehicleCard } from "../../../api/vehicle"

function defaultCard(): PublicVehicleCard {
  return {
    vehicleId: 0,
    brand: "",
    model: "",
    vehicleType: "",
    color: "",
    plateNoMask: "",
    certificationStatus: "UNSUBMITTED",
    isDefault: false
  }
}

Component({
  data: {
    loading: false,
    vehicleId: 0,
    card: defaultCard()
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
      this.loadCard(vehicleId)
    },
    async loadCard(vehicleId: number) {
      this.setData({ loading: true })
      try {
        const card = await getPublicVehicleCard(vehicleId)
        this.setData({ card })
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

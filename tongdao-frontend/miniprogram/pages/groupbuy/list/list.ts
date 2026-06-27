import { getGroupbuys, GroupbuyActivity } from "../../../api/groupbuy"

interface GroupbuyRow extends GroupbuyActivity {
  title: string
  merchant: string
  price: string
  oldPrice: string
  tags: string[]
  status: string
  progressText: string
}

Component({
  data: {
    loading: false,
    filters: ["全部", "拼团中", "已成团", "已结束"],
    products: [] as GroupbuyRow[]
  },
  lifetimes: {
    attached() {
      this.loadGroupbuys()
    }
  },
  methods: {
    async loadGroupbuys() {
      this.setData({ loading: true })
      try {
        const page = await getGroupbuys(undefined, 1, 20)
        this.setData({
          products: (page.records || []).map((item) => this.toRow(item))
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    toRow(item: GroupbuyActivity): GroupbuyRow {
      const current = item.currentPeople || 0
      const target = item.targetPeople || 0
      return {
        ...item,
        title: "拼团商品 #" + item.productId,
        merchant: "商家 #" + item.merchantId + " · " + current + "/" + target + " 人",
        price: String(item.groupPrice || 0),
        oldPrice: "",
        tags: ["车队专享价", "到店核销"],
        status: item.activityStatus || "UNKNOWN",
        progressText: current + "/" + target + " 人"
      }
    },
    goDetail(event: WechatMiniprogram.TouchEvent) {
      const id = Number(event.currentTarget.dataset.id || 0)
      if (!id) return
      wx.navigateTo({ url: "/pages/groupbuy/detail/detail?activityId=" + id })
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

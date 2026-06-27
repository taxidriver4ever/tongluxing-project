import { createOrder, Order, OrderPreview, previewOrder } from "../../../api/order"
import { createJsapiPayment } from "../../../api/payment"

function emptyPreview(): OrderPreview {
  return {
    productId: 0,
    activityId: 0,
    originalAmount: 0,
    groupbuyDiscountAmount: 0,
    couponDeductionAmount: 0,
    payableAmount: 0,
    selectedCouponId: 0,
    priceDescription: ""
  }
}

Component({
  data: {
    loading: false,
    paying: false,
    productId: 0,
    activityId: 0,
    quantity: 1,
    userCouponId: 0,
    useBestCoupon: true,
    openId: "",
    preview: emptyPreview(),
    order: null as Order | null
  },
  lifetimes: {
    attached() {
      this.loadFromOptions()
    }
  },
  methods: {
    onLoad(options: Record<string, string>) {
      this.init(options)
    },
    loadFromOptions() {
      const pages = getCurrentPages()
      const current = pages.length ? pages[pages.length - 1] as any : null
      this.init(current && current.options ? current.options : {})
    },
    init(options: Record<string, string>) {
      const productId = Number(options.productId || 0)
      const activityId = Number(options.activityId || 0)
      const quantity = Number(options.quantity || 1)
      const userCouponId = Number(options.userCouponId || 0)
      const openId = String(options.openId || "")
      this.setData({ productId, activityId, quantity, userCouponId, openId })
      if (productId) {
        this.loadPreview()
      }
    },
    async loadPreview() {
      this.setData({ loading: true })
      try {
        const preview = await previewOrder({
          productId: this.data.productId,
          activityId: this.data.activityId || undefined,
          quantity: this.data.quantity,
          userCouponId: this.data.userCouponId || undefined,
          useBestCoupon: this.data.useBestCoupon
        })
        this.setData({ preview })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    async pay() {
      if (!this.data.productId) {
        wx.showToast({ title: "缺少商品 ID", icon: "none" })
        return
      }
      this.setData({ paying: true })
      try {
        const order = await createOrder({
          productId: this.data.productId,
          activityId: this.data.activityId || undefined,
          quantity: this.data.quantity,
          userCouponId: this.data.userCouponId || undefined,
          useBestCoupon: this.data.useBestCoupon,
          requestId: this.makeRequestId()
        })
        this.setData({ order })
        if (!this.data.openId) {
          wx.showModal({
            title: "订单已创建",
            content: "后端支付接口需要 openId，但当前登录接口未返回 openId。请在微信登录链路补充后再发起支付。",
            showCancel: false,
            success: () => {
              wx.navigateTo({ url: "/pages/order/pay-success/pay-success?orderId=" + order.orderId })
            }
          })
          return
        }
        const payParams = await createJsapiPayment({ orderId: order.orderId, openId: this.data.openId })
        wx.requestPayment({
          timeStamp: payParams.timeStamp,
          nonceStr: payParams.nonceStr,
          "package": payParams.packageValue,
          signType: payParams.signType,
          paySign: payParams.paySign,
          success: () => {
            wx.navigateTo({ url: "/pages/order/pay-success/pay-success?orderId=" + order.orderId })
          },
          fail: (error) => {
            wx.showToast({ title: error.errMsg || "支付未完成", icon: "none" })
          }
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ paying: false })
      }
    },
    makeRequestId(): string {
      return "mp_" + Date.now() + "_" + Math.floor(Math.random() * 100000)
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

Component({
  data: {
    filters: ["沿途 20km", "车队专享", "可核销", "可退款"],
    products: [
      {
        title: "318 服务区牛肉面套餐",
        merchant: "距你 2.1km · L4 金牌商家",
        price: "39",
        oldPrice: "58",
        tags: ["车队专享价", "可到店核销"],
        status: "拼团中"
      },
      {
        title: "露营补给包 · 水果饮料",
        merchant: "距路线 4.8km · 今日可取",
        price: "68",
        oldPrice: "99",
        tags: ["补给优先", "可核销"],
        status: "可核销"
      },
      {
        title: "民宿车队房 · 双床含早餐",
        merchant: "距目的地 18km · L4 银牌商家",
        price: "288",
        oldPrice: "368",
        tags: ["车队价", "可退款"],
        status: "车队价"
      }
    ]
  },
  methods: {
    goDetail() {
      wx.navigateTo({ url: "/pages/groupbuy/detail/detail" })
    }
  }
})

Component({
  data: {
    teams: [
      {
        avatar: "川",
        theme: "blue",
        name: "川藏先锋",
        desc: "队长 ★4.9 · 2/4车",
        match: "92%",
        route: "广州 → 大理 · 预计 6.12-6.15",
        note: "拍照 + 美食 + AA住 · 含应急药箱",
        tags: ["中度同行", "同城优先", "可申请"]
      },
      {
        avatar: "滇",
        theme: "green",
        name: "滇西慢行队",
        desc: "队长 ★4.7 · 1/3车",
        match: "81%",
        route: "深圳 → 大理 · 预计 6.13 出发",
        note: "慢节奏 + 露营 + 景点同逛",
        tags: ["深度同行", "需审批"]
      }
    ]
  },
  methods: {
    goGroupbuyList() {
      wx.navigateTo({ url: "/pages/groupbuy/list/list" })
    }
  }
})

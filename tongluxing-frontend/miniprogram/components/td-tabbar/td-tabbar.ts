Component({
  properties: {
    active: {
      type: String,
      value: "map"
    }
  },
  data: {
    tabs: [
      { key: "map", label: "地图", url: "/pages/index/index" },
      { key: "trip", label: "行程", url: "/pages/trip/trip" },
      { key: "feed", label: "路途圈", url: "/pages/feed/feed" },
      { key: "message", label: "消息", url: "/pages/message/message" },
      { key: "profile", label: "我的", url: "/pages/profile/profile" }
    ]
  },
  methods: {
    onTap(event: WechatMiniprogram.TouchEvent) {
      const url = event.currentTarget.dataset.url
      if (url) {
        wx.redirectTo({ url })
      }
    }
  }
})

Component({
  properties: {
    active: {
      type: String,
      value: "map"
    }
  },
  data: {
    tabs: [
      { key: "map", label: "地图", icon: "map", url: "/pages/index/index" },
      { key: "message", label: "消息", icon: "message-circle", url: "/pages/message/message" },
      { key: "trip", label: "行程", icon: "route", url: "/pages/trip/trip" },
      { key: "profile", label: "我的", icon: "user-round", url: "/pages/profile/profile" }
    ]
  },
  methods: {
    onTap(event: WechatMiniprogram.TouchEvent) {
      const url = event.currentTarget.dataset.url
      if (url) {
        wx.reLaunch({ url })
      }
    }
  }
})

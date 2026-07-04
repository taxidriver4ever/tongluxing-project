Component({
  data: {
    homeStyle: ""
  },
  lifetimes: {
    attached() {
      this.initHomeSafeArea()
    }
  },
  methods: {
    initHomeSafeArea() {
      try {
        const system = wx.getSystemInfoSync()
        const menu = wx.getMenuButtonBoundingClientRect()
        const top = menu.top || system.statusBarHeight || 24
        this.setData({ homeStyle: "top: " + top + "px;" })
      } catch (error) {
        this.setData({ homeStyle: "top: 36px;" })
      }
    },
    goHome() {
      wx.reLaunch({ url: "/pages/index/index" })
    }
  }
})

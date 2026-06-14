Component({
  properties: {
    title: {
      type: String,
      value: ""
    },
    placeholder: {
      type: String,
      value: ""
    },
    action: {
      type: String,
      value: ""
    }
  },
  data: {
    showBack: true,
    topbarStyle: ""
  },
  lifetimes: {
    attached() {
      this.initNavigationSafeArea()
    }
  },
  methods: {
    initNavigationSafeArea() {
      const pages = getCurrentPages()
      const current = pages.length ? pages[pages.length - 1] : null
      const route = current ? current.route : ""
      const tabRoutes = [
        "pages/index/index",
        "pages/trip/trip",
        "pages/feed/feed",
        "pages/message/message",
        "pages/profile/profile"
      ]
      const showBack = tabRoutes.indexOf(route) < 0

      try {
        const system = wx.getSystemInfoSync()
        const menu = wx.getMenuButtonBoundingClientRect()
        const top = menu.top || system.statusBarHeight || 24
        const bottom = menu.bottom || top + 32
        const rightSafe = system.windowWidth - menu.left + 8
        const barHeight = bottom + 10
        this.setData({
          showBack,
          topbarStyle: "padding-top: " + top + "px; padding-right: " + rightSafe + "px; min-height: " + barHeight + "px;"
        })
      } catch (error) {
        this.setData({
          showBack,
          topbarStyle: "padding-top: 36px; padding-right: 112px; min-height: 78px;"
        })
      }
    },
    goBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        wx.navigateBack()
        return
      }
      wx.reLaunch({ url: "/pages/index/index" })
    }
  }
})

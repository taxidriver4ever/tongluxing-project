Component({
  methods: {
    goPublishTrip() {
      wx.navigateTo({
        url: "/pages/publish-trip/publish-trip"
      })
    },
    goSos() {
      wx.navigateTo({
        url: "/pages/sos/sos"
      })
    },
  }
})

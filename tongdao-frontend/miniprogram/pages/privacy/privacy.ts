Component({
  data: {
    loading: false,
    privacy: {
      profileVisible: true,
      phoneVisible: false,
      tripVisible: true,
      locationVisible: true,
      allowTeamInvite: true,
      allowPrivateMessage: true
    }
  },
  methods: {
    onSwitch(event: WechatMiniprogram.CustomEvent) {
      const key = event.currentTarget.dataset.key
      if (!key) {
        return
      }
      this.setData({
        ["privacy." + key]: event.detail.value
      })
    },
    onSave() {
      wx.showToast({ title: "当前后端暂未提供隐私设置接口", icon: "none" })
    }
  }
})

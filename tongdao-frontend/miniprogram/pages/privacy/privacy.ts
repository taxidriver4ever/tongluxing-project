import { getUserPrivacy, updateUserPrivacy } from "../../api/user"

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
  lifetimes: {
    attached() {
      this.loadPrivacy()
    }
  },
  methods: {
    async loadPrivacy() {
      this.setData({ loading: true })
      try {
        const privacy = await getUserPrivacy()
        this.setData({ privacy })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    onSwitch(event: any) {
      const key = event.currentTarget.dataset.key
      if (!key) {
        return
      }
      this.setData({
        ["privacy." + key]: event.detail.value
      })
    },
    async onSave() {
      this.setData({ loading: true })
      try {
        await updateUserPrivacy(this.data.privacy)
        wx.showToast({ title: "隐私设置已保存", icon: "success" })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

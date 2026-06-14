import { getCurrentUserProfile, updateUserProfile } from "../../api/user"

Component({
  data: {
    loading: false,
    form: {
      nickname: "",
      avatarUrl: "",
      gender: 0,
      birthday: "",
      cityCode: "",
      cityName: "",
      bio: ""
    }
  },
  lifetimes: {
    attached() {
      this.loadProfile()
    }
  },
  methods: {
    async loadProfile() {
      this.setData({ loading: true })
      try {
        const profile = await getCurrentUserProfile()
        this.setData({
          form: {
            nickname: profile.nickname || "",
            avatarUrl: profile.avatarUrl || "",
            gender: profile.gender || 0,
            birthday: profile.birthday || "",
            cityCode: profile.cityCode || "",
            cityName: profile.cityName || "",
            bio: profile.bio || ""
          }
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    onInput(event: any) {
      const field = event.currentTarget.dataset.field
      if (!field) {
        return
      }
      this.setData({
        ["form." + field]: event.detail.value
      })
    },
    onGenderChange(event: WechatMiniprogram.TouchEvent) {
      const gender = Number(event.currentTarget.dataset.gender || 0)
      this.setData({ "form.gender": gender })
    },
    async onSave() {
      if (!this.data.form.nickname) {
        wx.showToast({ title: "请填写昵称", icon: "none" })
        return
      }
      this.setData({ loading: true })
      try {
        await updateUserProfile(this.data.form)
        wx.showToast({ title: "资料已保存", icon: "success" })
        setTimeout(() => {
          wx.navigateBack()
        }, 500)
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

import { getCurrentUserProfile, updateUserProfile } from "../../api/user"
import { getImageUrl, uploadImage } from "../../api/storage"

Component({
  data: {
    loading: false,
    uploading: false,
    avatarUrl: "",
    form: { nickname:"", avatarImageKey:"", gender:0, birthday:"", cityCode:"", cityName:"", bio:"" }
  },
  lifetimes: { attached() { void this.loadProfile() } },
  methods: {
    async loadProfile() {
      this.setData({ loading:true })
      try {
        const profile = await getCurrentUserProfile()
        let avatarUrl = ""
        if (profile.avatarImageKey) {
          try { avatarUrl = await getImageUrl(profile.avatarImageKey) } catch (_error) { avatarUrl = "" }
        }
        this.setData({ avatarUrl, form:{ nickname:profile.nickname||"", avatarImageKey:profile.avatarImageKey||"", gender:profile.gender||0, birthday:profile.birthday||"", cityCode:profile.cityCode||"", cityName:profile.cityName||"", bio:profile.bio||"" } })
      } catch (error) { wx.showToast({ title:this.getErrorMessage(error), icon:"none" }) }
      finally { this.setData({ loading:false }) }
    },
    chooseAvatar() {
      if (this.data.uploading) return
      wx.chooseMedia({
        count:1,
        mediaType:["image"],
        sourceType:["album","camera"],
        success: async result => {
          const file = result.tempFiles && result.tempFiles[0]
          if (!file) return
          this.setData({ uploading:true, avatarUrl:file.tempFilePath })
          try {
            const key = await uploadImage(file.tempFilePath, "USER_AVATAR")
            this.setData({ "form.avatarImageKey":key })
            wx.showToast({ title:"头像已上传", icon:"success" })
          } catch (error) {
            this.setData({ avatarUrl:"" })
            wx.showToast({ title:this.getErrorMessage(error), icon:"none" })
          } finally { this.setData({ uploading:false }) }
        }
      })
    },
    onInput(event: WechatMiniprogram.Input) {
      const field = String(event.currentTarget.dataset.field || "")
      if (field) this.setData({ ["form." + field]: event.detail.value })
    },
    onGenderChange(event: WechatMiniprogram.TouchEvent) { this.setData({ "form.gender":Number(event.currentTarget.dataset.gender || 0) }) },
    onBirthdayChange(event: WechatMiniprogram.PickerChange) { this.setData({ "form.birthday":String(event.detail.value) }) },
    async onSave() {
      if (this.data.loading || this.data.uploading) return
      if (!this.data.form.nickname.trim()) { wx.showToast({ title:"请填写昵称", icon:"none" }); return }
      this.setData({ loading:true })
      try {
        await updateUserProfile({ ...this.data.form, nickname:this.data.form.nickname.trim(), cityName:this.data.form.cityName.trim(), cityCode:this.data.form.cityCode.trim(), bio:this.data.form.bio.trim() })
        wx.showToast({ title:"资料已保存", icon:"success" })
        setTimeout(() => wx.navigateBack(), 450)
      } catch (error) { wx.showToast({ title:this.getErrorMessage(error), icon:"none" }) }
      finally { this.setData({ loading:false }) }
    },
    getErrorMessage(error: unknown): string { return error instanceof Error ? error.message : "网络异常，请稍后重试" }
  }
})

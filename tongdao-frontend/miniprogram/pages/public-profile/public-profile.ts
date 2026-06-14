import { getCurrentUserProfile, getPublicUserProfile, PublicUserProfile } from "../../api/user"

function defaultPublicProfile(): PublicUserProfile {
  return {
    userId: 0,
    nickname: "同道车友",
    avatarUrl: "",
    cityName: "未设置城市",
    realNameStatus: "UNSUBMITTED",
    vehicleCertified: false
  }
}

Component({
  data: {
    loading: false,
    profile: defaultPublicProfile(),
    avatarText: "同"
  },
  lifetimes: {
    attached() {
      this.loadPublicProfile()
    }
  },
  methods: {
    async loadPublicProfile() {
      this.setData({ loading: true })
      try {
        const current = await getCurrentUserProfile()
        const profile = await getPublicUserProfile(current.userId)
        this.setData({
          profile,
          avatarText: profile.nickname ? profile.nickname.substring(0, 1) : "同"
        })
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

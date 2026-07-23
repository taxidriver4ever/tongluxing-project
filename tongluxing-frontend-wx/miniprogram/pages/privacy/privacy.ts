const KEY = "tlx-mini-privacy-settings"
interface PrivacySettings { profileVisible:boolean; phoneVisible:boolean; tripVisible:boolean; locationVisible:boolean; allowTeamInvite:boolean; allowPrivateMessage:boolean }
const DEFAULTS: PrivacySettings = { profileVisible:true, phoneVisible:false, tripVisible:true, locationVisible:true, allowTeamInvite:true, allowPrivateMessage:true }

Component({
  data: { loading:false, privacy:DEFAULTS },
  lifetimes: {
    attached() {
      const saved = wx.getStorageSync(KEY) as PrivacySettings | ""
      if (saved) this.setData({ privacy: { ...DEFAULTS, ...saved } })
    }
  },
  methods: {
    onSwitch(event: WechatMiniprogram.CustomEvent) {
      const key = String(event.currentTarget.dataset.key || "")
      if (key) this.setData({ ["privacy." + key]: event.detail.value })
    },
    onSave() {
      wx.setStorageSync(KEY, this.data.privacy)
      wx.showToast({ title:"设置已保存", icon:"success" })
    }
  }
})

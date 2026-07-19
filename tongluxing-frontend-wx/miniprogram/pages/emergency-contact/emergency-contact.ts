interface EmergencyContact {
  contactId: number
  contactName: string
  relation: string
  phoneMask: string
  isDefault: boolean
}

Component({
  data: {
    loading: false,
    contacts: [] as EmergencyContact[],
    form: {
      contactName: "",
      relation: "",
      phone: "",
      isDefault: false
    }
  },
  methods: {
    onInput(event: WechatMiniprogram.CustomEvent) {
      const field = event.currentTarget.dataset.field
      if (!field) {
        return
      }
      this.setData({
        ["form." + field]: event.detail.value
      })
    },
    onDefaultChange(event: WechatMiniprogram.CustomEvent) {
      this.setData({ "form.isDefault": event.detail.value })
    },
    onAdd() {
      wx.showToast({ title: "当前后端暂未提供紧急联系人接口", icon: "none" })
    },
    onDelete() {
      wx.showToast({ title: "当前后端暂未提供紧急联系人接口", icon: "none" })
    }
  }
})

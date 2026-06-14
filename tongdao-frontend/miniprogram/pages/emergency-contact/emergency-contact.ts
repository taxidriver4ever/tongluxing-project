import { addEmergencyContact, deleteEmergencyContact, EmergencyContact, getEmergencyContacts } from "../../api/user"

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
  lifetimes: {
    attached() {
      this.loadContacts()
    }
  },
  methods: {
    async loadContacts() {
      this.setData({ loading: true })
      try {
        const result = await getEmergencyContacts()
        this.setData({ contacts: result.contacts || [] })
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
    onDefaultChange(event: any) {
      this.setData({ "form.isDefault": event.detail.value })
    },
    async onAdd() {
      if (!this.data.form.contactName || !this.data.form.phone) {
        wx.showToast({ title: "请填写联系人和手机号", icon: "none" })
        return
      }
      this.setData({ loading: true })
      try {
        await addEmergencyContact(this.data.form)
        wx.showToast({ title: "联系人已保存", icon: "success" })
        this.setData({
          form: {
            contactName: "",
            relation: "",
            phone: "",
            isDefault: false
          }
        })
        await this.loadContacts()
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    onDelete(event: WechatMiniprogram.TouchEvent) {
      const contactId = Number(event.currentTarget.dataset.id || 0)
      if (!contactId) {
        return
      }
      wx.showModal({
        title: "删除联系人",
        content: "确认删除这个紧急联系人吗？",
        confirmText: "删除",
        success: async (result) => {
          if (!result.confirm) {
            return
          }
          try {
            await deleteEmergencyContact(contactId)
            wx.showToast({ title: "已删除", icon: "success" })
            await this.loadContacts()
          } catch (error) {
            wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
          }
        }
      })
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

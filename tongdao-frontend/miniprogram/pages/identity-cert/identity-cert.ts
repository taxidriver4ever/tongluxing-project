import { getIdentityStatus, submitIdentity } from "../../api/user"

Component({
  data: {
    loading: false,
    status: "UNSUBMITTED",
    rejectReason: "",
    idCardNoMask: "",
    form: {
      realName: "",
      idCardNo: "",
      faceImageUrl: ""
    }
  },
  lifetimes: {
    attached() {
      this.loadStatus()
    }
  },
  methods: {
    async loadStatus() {
      this.setData({ loading: true })
      try {
        const result = await getIdentityStatus()
        this.setData({
          status: result.status || "UNSUBMITTED",
          rejectReason: result.rejectReason || "",
          idCardNoMask: result.idCardNoMask || "",
          "form.realName": result.realName || "",
          "form.faceImageUrl": result.faceImageUrl || ""
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
    async onSubmit() {
      if (this.data.status === "PENDING") {
        wx.showToast({ title: "实名审核中，请勿重复提交", icon: "none" })
        return
      }
      if (!this.data.form.realName || !this.data.form.idCardNo) {
        wx.showToast({ title: "请填写真实姓名和证件号", icon: "none" })
        return
      }

      this.setData({ loading: true })
      try {
        const result = await submitIdentity(this.data.form)
        this.setData({
          status: result.status,
          rejectReason: result.rejectReason || "",
          idCardNoMask: result.idCardNoMask || ""
        })
        wx.showToast({ title: "实名资料已提交", icon: "success" })
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

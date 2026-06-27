import { submitCertification } from "../../api/user"

Component({
  data: {
    loading: false,
    status: "UNSUBMITTED",
    rejectReason: "",
    idCardNoMask: "",
    form: {
      realName: "",
      idCardNo: "",
      drivingLicenseImageKey: "",
      faceImageKey: ""
    }
  },
  lifetimes: {
    attached() {
      this.loadStatus()
    }
  },
  methods: {
    async loadStatus() {
      this.setData({ status: "UNSUBMITTED", rejectReason: "", idCardNoMask: "" })
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
      if (!this.data.form.realName || !this.data.form.idCardNo || !this.data.form.drivingLicenseImageKey || !this.data.form.faceImageKey) {
        wx.showToast({ title: "请填写实名资料和图片标识", icon: "none" })
        return
      }

      this.setData({ loading: true })
      try {
        const result = await submitCertification(this.data.form)
        this.setData({
          status: result.certificationStatus,
          rejectReason: result.rejectReason || "",
          idCardNoMask: ""
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

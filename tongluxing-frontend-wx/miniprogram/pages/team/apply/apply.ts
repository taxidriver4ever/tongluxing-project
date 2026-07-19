import { applyTeam, getTeam, Team } from "../../../api/team"

Component({
  data: {
    teamId: "",
    team: null as Team | null,
    applyMessage: "你好，我想加入车队一起同行。",
    loading: false
  },
  lifetimes: {
    attached() {
      this.loadFromOptions()
    }
  },
  methods: {
    onLoad(options: Record<string, string>) {
      this.init(options)
    },
    loadFromOptions() {
      const pages = getCurrentPages()
      const current = pages.length ? pages[pages.length - 1] as any : null
      const options = current && current.options ? current.options : {}
      this.init(options)
    },
    init(options: Record<string, string>) {
      const teamId = String(options.teamId || "")
      if (!teamId || this.data.teamId) {
        return
      }
      this.setData({ teamId })
      this.loadTeam(teamId)
    },
    async loadTeam(teamId: string) {
      try {
        const team = await getTeam(teamId)
        this.setData({ team })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      }
    },
    onInput(event: any) {
      this.setData({ applyMessage: event.detail.value })
    },
    async submit() {
      this.setData({ loading: true })
      try {
        await applyTeam(this.data.teamId, {
          applyMessage: this.data.applyMessage,
          joinQuestionJson: ""
        })
        wx.showModal({
          title: "申请已提交",
          content: "已发送给队长审批，通过后会自动加入车队。",
          showCancel: false,
          success() {
            wx.navigateBack()
          }
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

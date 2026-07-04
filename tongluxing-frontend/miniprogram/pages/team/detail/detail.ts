import { createTeamConversation } from "../../../api/chat"
import { exitTeam, getTeam, getTeamMembers, Team, TeamMember } from "../../../api/team"

function emptyTeam(): Team {
  return {
    teamId: "",
    tripId: "",
    ownerUserId: "",
    ownerVehicleId: "",
    teamName: "",
    teamDesc: "",
    startName: "",
    endName: "",
    departureTime: "",
    maxMemberCount: 0,
    currentMemberCount: 0,
    joinMode: "",
    teamStatus: "",
    publicFlag: true,
    chatConversationId: "",
    notice: "",
    createdAt: ""
  }
}

Component({
  data: {
    loading: false,
    teamId: "",
    team: emptyTeam(),
    members: [] as TeamMember[]
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
      this.setData({ loading: true })
      try {
        const team = await getTeam(teamId)
        const memberResult = await getTeamMembers(teamId)
        this.setData({ team, members: memberResult.members || [] })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    goApply() {
      wx.navigateTo({ url: "/pages/team/apply/apply?teamId=" + this.data.teamId })
    },
    async enterChat() {
      const team = this.data.team
      if (!team.teamId) {
        return
      }
      try {
        const conversation = await createTeamConversation(team.teamId, team.ownerUserId, team.teamName)
        wx.navigateTo({ url: "/pages/chat/team-room/room?conversationId=" + conversation.conversationId + "&groupId=team_" + team.teamId + "&title=" + encodeURIComponent(team.teamName) })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      }
    },
    onExit() {
      wx.showModal({
        title: "退出车队",
        content: "退出后将不再显示该车队群聊入口，确定继续吗？",
        confirmText: "退出",
        success: async (result) => {
          if (!result.confirm) {
            return
          }
          try {
            await exitTeam(this.data.teamId)
            wx.showToast({ title: "已退出车队", icon: "success" })
            setTimeout(() => wx.navigateBack(), 500)
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

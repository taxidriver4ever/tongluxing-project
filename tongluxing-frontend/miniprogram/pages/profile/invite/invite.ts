import { getMyInviteCode, getInvitationList, getInviteRewardProgress, InvitationRecord } from "../../../api/invite"
import { getToken } from "../../../utils/auth-storage"

// 阶梯奖励里程碑
const REWARD_STEPS = [
  { milestone: 5,  rewardDesc: "洗车券 ×1" },
  { milestone: 10, rewardDesc: "加油券 ×1" },
  { milestone: 30, rewardDesc: "保养券 ×1" },
  { milestone: 60, rewardDesc: "住宿大礼包" },
]

interface RewardStep {
  milestone: number
  rewardDesc: string
  done: boolean
  current: boolean
}

interface InviteRow {
  relationId: number
  avatarText: string
  statusDesc: string
  boundAt: string
  points: number
  pointClass: string
}

Page({
  data: {
    statusStyle: "",
    topbarStyle: "",
    loading: false,
    inviteCode: "------",
    validInviteCount: 0,
    nextRewardNeed: 5,
    nextMilestone: 5,
    inviteProgressPercent: 0,
    grantedDesc: "暂无",
    rewardSteps: [] as RewardStep[],
    recentInvitations: [] as InviteRow[]
  },

  onLoad() {
    this.initSafeArea()
    this.loadData()
  },

  initSafeArea() {
    try {
      const menu = wx.getMenuButtonBoundingClientRect()
      const sys = wx.getSystemInfoSync()
      const statusH = sys.statusBarHeight || 24
      // statusbar-space height
      const statusStyle = 'height: ' + statusH + 'px;'
      // topbar: height = capsule bottom - statusbar bottom; right padding avoids capsule
      const topbarH = Math.round(menu.bottom - statusH)
      const capsuleRightW = sys.screenWidth - menu.left
      const topbarStyle = 'height: ' + topbarH + 'px; padding-right: ' + capsuleRightW + 'px;'
      this.setData({ statusStyle, topbarStyle })
    } catch (_) {
      this.setData({
        statusStyle: 'height: 44px;',
        topbarStyle: 'height: 44px; padding-right: 190rpx;'
      })
    }
  },

  async loadData() {
    if (!getToken()) return
    this.setData({ loading: true })
    try {
      const [codeRes, progressRes, listRes] = await Promise.all([
        getMyInviteCode(),
        getInviteRewardProgress(),
        getInvitationList(undefined, 1, 10)
      ])

      const validCount = progressRes.validInviteCount || 0
      const nextRewardNeed = progressRes.nextRewardNeed || 5

      // 阶梯奖励状态
      const rewardSteps: RewardStep[] = REWARD_STEPS.map(step => ({
        ...step,
        done: validCount >= step.milestone,
        current: validCount < step.milestone && validCount >= (step.milestone - nextRewardNeed)
      }))

      // 找当前里程碑
      const currentStep = REWARD_STEPS.find(s => validCount < s.milestone)
      const nextMilestone = currentStep ? currentStep.milestone : REWARD_STEPS[REWARD_STEPS.length - 1].milestone
      const progressStart = REWARD_STEPS.findIndex(s => validCount < s.milestone) > 0
        ? REWARD_STEPS[REWARD_STEPS.findIndex(s => validCount < s.milestone) - 1].milestone
        : 0
      const inviteProgressPercent = Math.min(100, Math.round(((validCount - progressStart) / (nextMilestone - progressStart)) * 100))

      // 已解锁描述
      const granted = progressRes.grantedRuleCodes || []
      const grantedDesc = granted.length > 0 ? granted.join(" · ") : "暂无"

      // 最近邀请列表
      const recentInvitations: InviteRow[] = (listRes.records || []).map((r: InvitationRecord, idx: number) => {
        const done = !!r.firstTeamCompletedAt
        return {
          relationId: r.relationId,
          avatarText: "友" + (idx + 1),
          statusDesc: done ? "好友已完成首次组队" : "好友已注册 · 待完成同行",
          boundAt: this.formatDate(r.boundAt),
          points: done ? 100 : 50,
          pointClass: done ? "green" : "blue"
        }
      })

      this.setData({
        inviteCode: codeRes.inviteCode || "------",
        validInviteCount: validCount,
        nextRewardNeed,
        nextMilestone,
        inviteProgressPercent,
        grantedDesc,
        rewardSteps,
        recentInvitations
      })
    } catch (_) {
      wx.showToast({ title: "加载失败，请重试", icon: "none" })
    } finally {
      this.setData({ loading: false })
    }
  },

  formatDate(dateStr: string): string {
    if (!dateStr) return ""
    try {
      const d = new Date(dateStr)
      return (d.getMonth() + 1) + "月" + d.getDate() + "日"
    } catch (_) {
      return dateStr.substring(0, 10)
    }
  },

  goBack() { wx.navigateBack() },

  shareInvite() {
    wx.showShareMenu({ withShareTicket: true })
  },

  saveCard() {
    wx.showToast({ title: "保存功能即将上线", icon: "none" })
  },

  goAllInvitations() {
    wx.showToast({ title: "完整邀请记录页即将上线", icon: "none" })
  }
})

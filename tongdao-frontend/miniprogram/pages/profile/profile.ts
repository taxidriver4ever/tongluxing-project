import { getUserDashboard, UserDashboard, GrowthSummary } from "../../api/user"
import { getMyVehicles, Vehicle } from "../../api/vehicle"
import { getToken } from "../../utils/auth-storage"

// 等级映射表
const LEVEL_MAP: Record<string, { name: string; maxPoints: number; nextName: string }> = {
  "Lv1": { name: "Lv.1 同路新人", maxPoints: 500,  nextName: "Lv.2" },
  "Lv2": { name: "Lv.2 同路旅人", maxPoints: 1500, nextName: "Lv.3" },
  "Lv3": { name: "Lv.3 同路先锋", maxPoints: 5000, nextName: "Lv.4" },
  "Lv4": { name: "Lv.4 同路达人", maxPoints: 12000, nextName: "Lv.5" },
  "Lv5": { name: "Lv.5 同路领袖", maxPoints: 999999, nextName: "顶级" },
}

function defaultDashboard(): UserDashboard {
  return {
    profile: {
      userId: 0,
      nickname: "同道车友",
      avatarImageKey: "",
      gender: 0,
      birthday: "",
      cityCode: "",
      cityName: "未设置城市",
      bio: "",
      profileStatus: "INCOMPLETE",
      certificationStatus: "UNSUBMITTED"
    },
    growth: { totalPoints: 0, levelCode: "Lv1", nextLevelPoints: 500 },
    coupon: { availableCount: 0, expiringCount: 0 },
    invitation: { validInviteCount: 0, nextRewardNeed: 5 },
    nextTripDraft: null
  }
}

Component({
  data: {
    loading: false,
    dashboard: defaultDashboard() as UserDashboard,
    defaultVehicle: null as Vehicle | null,
    // 计算属性 —— header
    headStyle: "",
    avatarText: "同",
    certPillText: "未认证",
    certPillClass: "",
    levelName: "Lv.1 同路新人",
    nextLevelName: "Lv.2",
    nextLevelPoints: 500,
    totalPoints: 0,
    progressPercent: 0,
    vehicleLabel: "未添加车辆",
    // 车辆认证状态 (第三卡片)
    headVehicleStatusText: "未认证",
    headVehicleStatusClass: "",
    // 功能入口文案
    inviteHint: "邀请好友一起上路",
    nextTripLabel: "暂无草稿行程"
  },

  lifetimes: {
    attached() {
      this.initSafeArea()
    }
  },

  pageLifetimes: {
    show() {
      this.loadDashboard()
    }
  },

  methods: {
    initSafeArea() {
      try {
        const menu = wx.getMenuButtonBoundingClientRect()
        const system = wx.getSystemInfoSync()
        const top = menu.top || system.statusBarHeight || 24
        this.setData({ headStyle: "padding-top: " + top + "px;" })
      } catch (_) {
        this.setData({ headStyle: "padding-top: 44px;" })
      }
    },

    async loadDashboard() {
      if (!getToken()) {
        wx.showToast({ title: "请先登录", icon: "none" })
        wx.navigateTo({ url: "/pages/login/login" })
        return
      }
      this.setData({ loading: true })
      try {
        const [dashboard, vehicleList] = await Promise.all([
          getUserDashboard(),
          getMyVehicles()
        ])
        const vehicles = vehicleList.vehicles || []
        const defaultVehicle = this.findDefaultVehicle(vehicles)
        this.setData({
          dashboard,
          defaultVehicle,
          ...this.computeDerivedData(dashboard, defaultVehicle)
        })
      } catch (err) {
        wx.showToast({ title: this.errMsg(err), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },

    computeDerivedData(dashboard: UserDashboard, vehicle: Vehicle | null) {
      const profile = dashboard.profile
      const growth = dashboard.growth
      const invite = dashboard.invitation

      // 头像文字
      const avatarText = profile.nickname ? profile.nickname.substring(0, 1) : "同"

      // 认证状态药丸
      const certStatus = profile.certificationStatus
      let certPillText = "未实名"
      let certPillClass = ""
      if (certStatus === "APPROVED") { certPillText = "已认证车主"; certPillClass = "green" }
      else if (certStatus === "PENDING") { certPillText = "审核中"; certPillClass = "orange" }
      else if (certStatus === "REJECTED") { certPillText = "认证被拒"; certPillClass = "red" }

      // 等级与进度
      const lv = LEVEL_MAP[growth.levelCode] || LEVEL_MAP["Lv1"]
      const levelName = lv.name
      const nextLevelName = lv.nextName
      const nextLevelPoints = growth.nextLevelPoints || 0
      const totalPoints = growth.totalPoints || 0
      const usedPoints = lv.maxPoints - nextLevelPoints
      const progressPercent = lv.maxPoints > 0
        ? Math.min(100, Math.round((usedPoints / lv.maxPoints) * 100))
        : 0

      // 车辆标签
      let vehicleLabel = "未添加车辆"
      let headVehicleStatusText = "未认证"
      let headVehicleStatusClass = ""
      if (vehicle) {
        vehicleLabel = (vehicle.brand || "") + " " + (vehicle.model || "")
        const cs = vehicle.certificationStatus
        if (cs === "APPROVED") { headVehicleStatusText = "已认证"; headVehicleStatusClass = "green" }
        else if (cs === "PENDING") { headVehicleStatusText = "审核中"; headVehicleStatusClass = "orange" }
        else if (cs === "REJECTED") { headVehicleStatusText = "认证被拒"; headVehicleStatusClass = "red" }
        else { headVehicleStatusText = "待认证" }
      }

      // 邀请文案
      const nextReward = invite.nextRewardNeed || 0
      const inviteHint = nextReward > 0
        ? "再邀 " + nextReward + " 人得洗车券"
        : "老带新一起上路"

      // 下一趟行程
      const draft = dashboard.nextTripDraft
      let nextTripLabel = "暂无草稿行程"
      if (draft && draft.startLocation && draft.endLocation) {
        nextTripLabel = (draft.startLocation.name || "出发地") + "→" + (draft.endLocation.name || "目的地") + " 草稿"
      }

      return {
        avatarText, certPillText, certPillClass,
        levelName, nextLevelName, nextLevelPoints, totalPoints, progressPercent,
        vehicleLabel, headVehicleStatusText, headVehicleStatusClass,
        inviteHint, nextTripLabel
      }
    },

    findDefaultVehicle(vehicles: Vehicle[]): Vehicle | null {
      if (!vehicles.length) return null
      for (let i = 0; i < vehicles.length; i++) {
        if (vehicles[i].isDefault) return vehicles[i]
      }
      return vehicles[0]
    },

    // ─── 导航 ──────────────────────────────────────────────────
    goProfileEdit() {
      wx.navigateTo({ url: "/pages/profile-edit/profile-edit" })
    },
    goGrowth() {
      wx.navigateTo({ url: "/pages/profile/growth/growth" })
    },
    goCoupon() {
      wx.navigateTo({ url: "/pages/profile/coupon/coupon" })
    },
    goInvite() {
      wx.navigateTo({ url: "/pages/profile/invite/invite" })
    },
    goNextTrip() {
      const draft = this.data.dashboard.nextTripDraft
      if (draft && draft.draftId) {
        wx.navigateTo({ url: "/pages/publish-trip/publish-trip?draftId=" + draft.draftId })
      } else {
        wx.navigateTo({ url: "/pages/publish-trip/publish-trip" })
      }
    },
    goGroupbuy() {
      wx.navigateTo({ url: "/pages/groupbuy/list/list" })
    },
    goVerification() {
      wx.navigateTo({ url: "/pages/order/verification-code/verification-code" })
    },
    goVehicleCert() {
      const v = this.data.defaultVehicle
      if (v && v.vehicleId) {
        wx.navigateTo({ url: "/pages/vehicle/certification/certification?vehicleId=" + v.vehicleId })
      } else {
        wx.navigateTo({ url: "/pages/vehicle/list/list" })
      }
    },
    goPrivacy() {
      wx.navigateTo({ url: "/pages/privacy/privacy" })
    },
    goEmergencyContact() {
      wx.navigateTo({ url: "/pages/emergency-contact/emergency-contact" })
    },

    errMsg(err: unknown): string {
      return err instanceof Error ? err.message : "网络异常，请稍后重试"
    }
  }
})

import { getGrowthSummary, getGrowthLogs, getBadgeWall, GrowthSummary, GrowthLog, Badge } from "../../../api/user"
import { getToken } from "../../../utils/auth-storage"

// 等级配置
const LEVEL_CONFIG: Record<string, { name: string; maxPoints: number; nextName: string; perks: string }> = {
  "Lv1": { name: "Lv.1 同路新人",  maxPoints: 500,   nextName: "Lv.2", perks: "基础匹配 · 浏览行程与拼团" },
  "Lv2": { name: "Lv.2 同路旅人",  maxPoints: 1500,  nextName: "Lv.3", perks: "加速匹配 · 每月可领优惠券" },
  "Lv3": { name: "Lv.3 同路先锋",  maxPoints: 5000,  nextName: "Lv.4", perks: "优先匹配 · 同路值加速 ×1.2 · 每月可领洗车券" },
  "Lv4": { name: "Lv.4 同路达人",  maxPoints: 12000, nextName: "Lv.5", perks: "优先队长 · 同路值加速 ×1.5 · 专属达人标识" },
  "Lv5": { name: "Lv.5 同路领袖",  maxPoints: 999999, nextName: "顶级", perks: "全平台优先 · 专属客服通道 · 领袖勋章" },
}

// 今日任务（本地枚举，后端暂无单独接口）
const TODAY_TASKS = [
  { label: "每日登录",     done: true,  action: false, badge: "+5 已完成" },
  { label: "发布路途圈动态", done: false, action: true,  badge: "+10 去完成" },
  { label: "邀请好友注册",  done: false, action: true,  badge: "+50 去邀请" },
  { label: "完成一次同行",  done: false, action: false, badge: "+120 待触发" },
]

// 勋章图标映射（按 badgeCode 对应，不在 API 中返回 emoji）
const BADGE_ICON_MAP: Record<string, { icon: string; orange?: boolean }> = {
  "FIRST_TRIP":     { icon: "🚗" },
  "MILEAGE_1000":   { icon: "🏆" },
  "HOT_CAPTAIN":    { icon: "🔥", orange: true },
  "HELPFUL":        { icon: "🤝" },
  "SOCIAL_STAR":    { icon: "📸" },
  "CROSS_CITY":     { icon: "🌏", orange: true },
  "MILEAGE_10000":  { icon: "🛣" },
  "POPULAR":        { icon: "👑" },
  "PRECISE_DRIVER": { icon: "🎯" },
}

interface BadgeItem extends Badge {
  icon: string
  orange: boolean
  earned: boolean
}

Page({
  data: {
    safeStyle: "",
    topbarStyle: "",
    loading: false,
    // 成长
    levelName: "Lv.1 同路新人",
    levelPerks: "基础匹配 · 浏览行程与拼团",
    nextLevelName: "Lv.2",
    nextLevelPoints: 500,
    levelMaxPoints: 500,
    totalPoints: 0,
    progressPercent: 0,
    monthlyPoints: 0,
    // 任务
    todayTasks: TODAY_TASKS,
    earnedToday: 5,
    totalToday: 185,
    // 勋章
    badges: [] as BadgeItem[],
    earnedBadgeCount: 0,
    totalBadgeCount: 0,
    // 日志
    growthLogs: [] as GrowthLog[]
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
      // 顶部安全区：从状态栏底部开始
      const safeStyle = 'padding-top: ' + statusH + 'px;'
      // 胶囊按钮右侧到屏幕边缘的宽度，留出这段空间避免遮挡
      const capsuleRightW = sys.screenWidth - menu.left
      // topbar 高度对齐到胶囊底部（即胶囊.bottom - statusBarHeight）
      const topbarH = Math.round(menu.bottom - statusH)
      const topbarStyle = 'height: ' + topbarH + 'px; padding-right: ' + capsuleRightW + 'px;'
      this.setData({ safeStyle, topbarStyle })
    } catch (_) {
      this.setData({
        safeStyle: 'padding-top: 44px;',
        topbarStyle: 'height: 44px; padding-right: 190rpx;'
      })
    }
  },

  async loadData() {
    if (!getToken()) return
    this.setData({ loading: true })
    try {
      const [growth, badgeWall, logsPage] = await Promise.all([
        getGrowthSummary(),
        getBadgeWall(),
        getGrowthLogs(1, 10)
      ])
      const lv = LEVEL_CONFIG[growth.levelCode] || LEVEL_CONFIG["Lv1"]
      const usedPoints = lv.maxPoints - (growth.nextLevelPoints || 0)
      const progressPercent = Math.min(100, Math.round((usedPoints / lv.maxPoints) * 100))

      // 合并 earned + locked 勋章
      const earnedSet = new Set(badgeWall.earned.map((b: Badge) => b.badgeCode))
      const allBadges: BadgeItem[] = [
        ...badgeWall.earned.map((b: Badge) => {
          const meta = BADGE_ICON_MAP[b.badgeCode] || { icon: "🎖" }
          return { ...b, icon: meta.icon, orange: !!meta.orange, earned: true }
        }),
        ...badgeWall.locked.map((b: Badge) => {
          const meta = BADGE_ICON_MAP[b.badgeCode] || { icon: "🎖" }
          return { ...b, icon: meta.icon, orange: !!meta.orange, earned: false }
        })
      ]

      this.setData({
        levelName: lv.name,
        levelPerks: lv.perks,
        nextLevelName: lv.nextName,
        nextLevelPoints: growth.nextLevelPoints || 0,
        levelMaxPoints: lv.maxPoints,
        totalPoints: growth.totalPoints || 0,
        progressPercent,
        monthlyPoints: 0, // 后端暂无月增量接口
        badges: allBadges,
        earnedBadgeCount: badgeWall.earned.length,
        totalBadgeCount: allBadges.length,
        growthLogs: logsPage.records || []
      })
    } catch (err) {
      wx.showToast({ title: "加载失败，请重试", icon: "none" })
    } finally {
      this.setData({ loading: false })
    }
  },

  goBack() {
    wx.navigateBack()
  },

  showRules() {
    wx.showModal({
      title: "成长规则",
      content: "完成同行 +120 · 担任队长 +50 · 邀请好友注册 +50 · 好友完成首次组队 +100 · 每日登录 +5 · 发帖动态 +10",
      showCancel: false
    })
  },

  showAllBadges() {
    wx.showToast({ title: "完整勋章墙即将上线", icon: "none" })
  },

  goGrowthLogs() {
    wx.showToast({ title: "成长日志完整页即将上线", icon: "none" })
  }
})

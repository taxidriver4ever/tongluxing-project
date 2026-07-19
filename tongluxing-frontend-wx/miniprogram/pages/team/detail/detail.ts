import { CoreTeam, getMockTeam } from "../../../data/mock-core"

Page({
  data: { safeTop: 44, team: {} as CoreTeam },
  onLoad(options: Record<string, string>) { try { this.setData({ safeTop: wx.getSystemInfoSync().statusBarHeight || 44 }) } catch (_) {} getMockTeam(options.teamId || "TEAM01").then(team => this.setData({ team })) },
  back() { wx.navigateBack({ fail: () => wx.reLaunch({ url: "/pages/index/index" }) }) },
  route() { wx.navigateTo({ url: "/pages/trip/detail/detail?tripId=T1001" }) },
  members() { wx.navigateTo({ url: "/pages/team/members/members" }) },
  chat() { wx.navigateTo({ url: "/pages/chat/session/session?id=CHAT01" }) },
  manage() { wx.navigateTo({ url: "/pages/team/manage/manage" }) },
  join() { wx.showToast({ title: "申请已提交", icon: "success" }) }
})

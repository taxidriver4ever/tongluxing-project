import { mockUser } from "../../data/mock-core"
import { getNavigationLayout } from "../../utils/navigation-layout"

Page({
  data: { contentTop: 82, user: mockUser },
  onLoad() { this.setData({ contentTop: getNavigationLayout().contentTop }) },
  goEdit() { wx.navigateTo({ url: "/pages/profile-edit/profile-edit" }) },
  goVehicle() { wx.navigateTo({ url: "/pages/vehicle/list/list" }) },
  goBadges() { wx.navigateTo({ url: "/pages/profile/badges/badges" }) },
  goGrowth() { wx.navigateTo({ url: "/pages/profile/growth/growth" }) },
  goInvite() { wx.navigateTo({ url: "/pages/profile/invite/invite" }) },
  goCoupon() { wx.navigateTo({ url: "/pages/profile/coupon/coupon" }) },
  goSettings() { wx.navigateTo({ url: "/pages/profile/settings/settings" }) }
})

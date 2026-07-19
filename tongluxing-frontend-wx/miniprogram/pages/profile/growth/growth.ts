import { mockBadges } from "../../../data/mock-core"
Page({ data: { badges: mockBadges.filter(item => item.earned) }, back() { wx.navigateBack() }, badges() { wx.navigateTo({ url: "/pages/profile/badges/badges" }) } })

import { mockCoupons } from "../../../data/mock-core"
Page({ data: { coupon: mockCoupons[0] }, onLoad(options: Record<string, string>) { const coupon = mockCoupons.find(item => item.id === options.id) || mockCoupons[0]; this.setData({ coupon }) }, use() { wx.showToast({ title: "已选择该优惠券", icon: "success" }) } })

import { mockFriendRequests } from "../../../data/mock-core"

Page({ data: { requests: mockFriendRequests }, accept(event: WechatMiniprogram.TouchEvent) { const id = event.currentTarget.dataset.id; this.setData({ requests: this.data.requests.map(item => item.id === id ? { ...item, accepted: true } : item) }); wx.showToast({ title: "已添加好友", icon: "success" }) } })

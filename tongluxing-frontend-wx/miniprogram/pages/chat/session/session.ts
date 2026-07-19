import { mockMessages } from "../../../data/mock-core"
import { getNavigationLayout } from "../../../utils/navigation-layout"

Page({
  data: { contentTop: 82, draft: "", messages: mockMessages },
  onLoad() { this.setData({ contentTop: getNavigationLayout().contentTop }) },
  back() { wx.navigateBack() },
  input(event: WechatMiniprogram.Input) { this.setData({ draft: event.detail.value }) },
  send() {
    const content = this.data.draft.trim()
    if (!content) return
    this.setData({ messages: this.data.messages.concat([{ id: Date.now(), mine: true, avatar: "我", sender: "我", content, time: "刚刚" }]), draft: "" })
  },
  detail() { wx.navigateTo({ url: "/pages/chat/detail/detail" }) }
})

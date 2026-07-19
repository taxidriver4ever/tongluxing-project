import { CoreConversation, getMockConversations } from "../../data/mock-core"
import { getNavigationLayout } from "../../utils/navigation-layout"

Page({
  data: { contentTop: 82, chats: [] as CoreConversation[] },
  onLoad() { this.setData({ contentTop: getNavigationLayout().contentTop }) },
  onShow() { getMockConversations().then(chats => this.setData({ chats })) },
  open(event: WechatMiniprogram.TouchEvent) { wx.navigateTo({ url: "/pages/chat/session/session?id=" + event.currentTarget.dataset.id }) },
  friendRequests() { wx.navigateTo({ url: "/pages/chat/friend-request/friend-request" }) }
})

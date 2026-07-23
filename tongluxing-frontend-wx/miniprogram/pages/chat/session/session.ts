import { Conversation, Message, getConversations, getMessages, sendMessage } from "../../../api/chat"
import { getUserId } from "../../../utils/auth-storage"
import { getNavigationLayout } from "../../../utils/navigation-layout"
interface MessageView extends Message { mine: boolean; avatarText: string; timeText: string }
function timeText(value: string): string { const date = new Date(value); return Number.isNaN(date.getTime()) ? value : String(date.getHours()).padStart(2,"0") + ":" + String(date.getMinutes()).padStart(2,"0") }
function messageView(message: Message): MessageView { const name = message.senderNickname || "同行成员"; return { ...message, mine: message.senderUserId === String(getUserId()), avatarText: name.slice(0,1), timeText: timeText(message.sentAt) } }
Page({
  data: { contentTop: 82, conversationId: "", conversation: null as Conversation | null, draft: "", messages: [] as MessageView[], loading: true, sending: false, bottomAnchor: "bottom" },
  onLoad(options: Record<string,string>) { this.setData({ contentTop: getNavigationLayout().contentTop, conversationId: String(options.id || "") }); void this.load() },
  async load() {
    if (!this.data.conversationId) { wx.showToast({ title: "缺少会话编号", icon: "none" }); return }
    try {
      const [list, messageList] = await Promise.all([getConversations(), getMessages(this.data.conversationId)])
      const conversation = list.conversations.find(row => row.conversationId === this.data.conversationId) || null
      this.setData({ conversation, messages: messageList.messages.map(messageView), loading: false, bottomAnchor: "bottom-" + Date.now() })
    } catch (error) { this.setData({ loading: false }); wx.showToast({ title: error instanceof Error ? error.message : "消息加载失败", icon: "none" }) }
  },
  back() { wx.navigateBack() },
  input(event: WechatMiniprogram.Input) { this.setData({ draft: event.detail.value }) },
  async send() {
    const content = this.data.draft.trim(); if (!content || this.data.sending) return
    this.setData({ sending: true })
    try { const message = await sendMessage(this.data.conversationId, content); this.setData({ messages: this.data.messages.concat(messageView(message)), draft: "", bottomAnchor: "bottom-" + Date.now() }) }
    catch (error) { wx.showToast({ title: error instanceof Error ? error.message : "发送失败", icon: "none" }) }
    finally { this.setData({ sending: false }) }
  },
  detail() { const row = this.data.conversation; let url = "/pages/chat/detail/detail?id=" + this.data.conversationId; if (row && row.bizId) url += "&tripId=" + row.bizId; wx.navigateTo({ url }) }
})

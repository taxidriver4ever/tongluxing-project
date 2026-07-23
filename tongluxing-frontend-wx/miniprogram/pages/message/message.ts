import { Conversation, getConversations, updateConversationSettings } from "../../api/chat"
import { getNavigationLayout } from "../../utils/navigation-layout"

interface ConversationView extends Conversation { avatarText: string; displayTime: string }
function timeText(value: string): string {
  if (!value) return ""
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.slice(0, 10)
  const now = new Date()
  if (date.toDateString() === now.toDateString()) return String(date.getHours()).padStart(2,"0") + ":" + String(date.getMinutes()).padStart(2,"0")
  return (date.getMonth()+1) + "/" + date.getDate()
}
function view(row: Conversation): ConversationView {
  const name = row.conversationName || "车队会话"
  return { ...row, avatarText: name.slice(0,2), displayTime: timeText(row.lastMessageAt) }
}
Page({
  data: { contentTop: 82, keyword: "", loading: true, allChats: [] as ConversationView[], chats: [] as ConversationView[] },
  onLoad() { this.setData({ contentTop: getNavigationLayout().contentTop }) },
  onShow() { void this.load() },
  async load() {
    this.setData({ loading: true })
    try {
      const data = await getConversations(); const allChats = data.conversations.map(view)
      this.setData({ allChats, chats: this.filter(allChats, this.data.keyword) })
    } catch (error) { wx.showToast({ title: error instanceof Error ? error.message : "会话加载失败", icon: "none" }) }
    finally { this.setData({ loading: false }) }
  },
  filter(rows: ConversationView[], keyword: string): ConversationView[] {
    const normalized = keyword.trim().toLowerCase(); return normalized ? rows.filter(item => item.conversationName.toLowerCase().includes(normalized)) : rows
  },
  search(event: WechatMiniprogram.Input) { const keyword = event.detail.value; this.setData({ keyword, chats: this.filter(this.data.allChats, keyword) }) },
  clear() { this.setData({ keyword: "", chats: this.data.allChats }) },
  open(event: WechatMiniprogram.TouchEvent) { wx.navigateTo({ url: "/pages/chat/session/session?id=" + event.currentTarget.dataset.id }) },
  friendRequests() { wx.navigateTo({ url: "/pages/chat/friend-request/friend-request" }) },
  async togglePin(event: WechatMiniprogram.TouchEvent) {
    const id = String(event.currentTarget.dataset.id); const row = this.data.allChats.find(item => item.conversationId === id); if (!row) return
    try { await updateConversationSettings(id, { muted: Boolean(row.muted), pinned: !row.pinned }); await this.load() } catch (error) { wx.showToast({ title: error instanceof Error ? error.message : "操作失败", icon: "none" }) }
  }
})

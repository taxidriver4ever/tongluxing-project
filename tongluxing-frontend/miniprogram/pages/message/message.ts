import { Conversation, getConversations } from "../../api/chat"

function avatarText(name: string): string {
  if (!name) {
    return "聊"
  }
  return name.slice(0, 1)
}

Component({
  data: {
    loading: false,
    chats: [] as Array<Conversation & { avatar: string; theme: string; badge: string }>
  },
  pageLifetimes: {
    show() {
      this.loadConversations()
    }
  },
  methods: {
    async loadConversations() {
      this.setData({ loading: true })
      try {
        const result = await getConversations()
        const chats = (result.conversations || []).map((item) => {
          return {
            conversationId: item.conversationId,
            bizType: item.bizType,
            bizId: item.bizId,
            conversationName: item.conversationName,
            conversationStatus: item.conversationStatus,
            providerType: item.providerType,
            lastMessagePreview: item.lastMessagePreview,
            lastMessageAt: item.lastMessageAt,
            avatar: avatarText(item.conversationName),
            theme: item.bizType === "TEAM" ? "blue" : "green",
            badge: ""
          }
        })
        this.setData({ chats })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    openChat(event: WechatMiniprogram.TouchEvent) {
      const id = String(event.currentTarget.dataset.id || "")
      const title = String(event.currentTarget.dataset.title || "车队群聊")
      const groupId = String(event.currentTarget.dataset.group || "")
      if (id) {
        wx.navigateTo({ url: "/pages/chat/team-room/room?conversationId=" + id + "&groupId=" + groupId + "&title=" + encodeURIComponent(title) })
      }
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

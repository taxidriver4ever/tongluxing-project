import { getGroupMessageList, ImTextMessage, loginTencentIm, offImTextMessage, onImTextMessage, sendGroupTextMessage } from "../../../utils/im-client"

Component({
  data: {
    conversationId: "",
    groupId: "",
    title: "车队群聊",
    messages: [] as ImTextMessage[],
    content: "",
    loading: false,
    imReady: false,
    nextReqMessageID: "",
    historyCompleted: false,
    bottomId: "bottom-anchor"
  },
  lifetimes: {
    attached() {
      this.loadFromOptions()
    },
    detached() {
      offImTextMessage()
    }
  },
  methods: {
    onLoad(options: Record<string, string>) {
      this.init(options)
    },
    loadFromOptions() {
      const pages = getCurrentPages()
      const current = pages.length ? pages[pages.length - 1] as any : null
      const options = current && current.options ? current.options : {}
      this.init(options)
    },
    init(options: Record<string, string>) {
      const conversationId = String(options.conversationId || "")
      const groupId = String(options.groupId || "")
      const title = decodeURIComponent(String(options.title || "车队群聊"))
      if (!conversationId || this.data.conversationId) {
        return
      }
      this.setData({
        conversationId,
        groupId: groupId || ("team_" + String(options.teamId || "")),
        title
      })
      this.initIm()
    },
    async initIm() {
      if (!this.data.groupId) {
        wx.showToast({ title: "缺少群聊 ID", icon: "none" })
        return
      }
      this.setData({ loading: true })
      try {
        await loginTencentIm()
        this.setData({ imReady: true })
        onImTextMessage((message) => {
          this.setData({
            messages: this.data.messages.concat([message])
          })
          this.scrollToBottom()
        })
        await this.loadMessages()
      } catch (error) {
        wx.showToast({ title: "腾讯云 IM 登录失败：" + this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    async loadMessages() {
      if (!this.data.imReady || this.data.historyCompleted) {
        return
      }
      try {
        const result = await getGroupMessageList(this.data.groupId, this.data.nextReqMessageID)
        this.setData({
          messages: result.messages.concat(this.data.messages),
          nextReqMessageID: result.nextReqMessageID,
          historyCompleted: result.completed
        })
        this.scrollToBottom()
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      }
    },
    onInput(event: any) {
      this.setData({ content: event.detail.value })
    },
    async onSend() {
      const content = this.data.content
      if (!content) {
        wx.showToast({ title: "请输入消息", icon: "none" })
        return
      }
      try {
        const message = await sendGroupTextMessage(this.data.groupId, content)
        this.setData({
          messages: this.data.messages.concat([message]),
          content: ""
        })
        this.scrollToBottom()
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      }
    },
    scrollToBottom() {
      setTimeout(() => {
        this.setData({ bottomId: "bottom-anchor" })
      }, 50)
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

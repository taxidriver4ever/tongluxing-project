import { LoginResponse, setPassword, wxPhoneLogin } from "../../../api/auth"
import { bindInvite, getMyInviteCode } from "../../../api/invite"
import { getGrowthAccount, getGrowthLogs } from "../../../api/growth"
import { saveSession } from "../../../utils/auth-storage"

Page({
  data: {
    aPhone: "13800000001",
    bPhone: "13800000002",
    deviceId: "invite-growth-debug",
    password: "123456",
    inviteCode: "",
    aUserId: 0,
    bUserId: 0,
    aSession: null as LoginResponse | null,
    bSession: null as LoginResponse | null,
    running: false,
    logs: [] as string[]
  },
  input(event: WechatMiniprogram.Input) {
    const field = String(event.currentTarget.dataset.field)
    this.setData({ [field]: event.detail.value })
  },
  append(message: string) {
    this.setData({ logs: this.data.logs.concat(new Date().toLocaleTimeString() + "  " + message) })
  },
  activate(session: LoginResponse) {
    saveSession({ token: session.token, refreshToken: session.refreshToken, userId: session.userId })
  },
  async loginA() {
    const session = await wxPhoneLogin({ mockPhone: this.data.aPhone, deviceId: this.data.deviceId + "-A" })
    this.activate(session)
    const code = await getMyInviteCode()
    this.setData({ aSession: session, aUserId: session.userId, inviteCode: code.inviteCode })
    this.append("用户A登录成功，邀请码：" + code.inviteCode)
  },
  async loginB() {
    if (!this.data.inviteCode) throw new Error("请先登录用户A并生成邀请码")
    const session = await wxPhoneLogin({ mockPhone: this.data.bPhone, deviceId: this.data.deviceId + "-B", inviteCode: this.data.inviteCode })
    this.activate(session)
    this.setData({ bSession: session, bUserId: session.userId })
    this.append("用户B登录成功，首次注册：" + session.isNewUser)
  },
  async setBPassword() {
    if (!this.data.bSession) throw new Error("请先登录用户B")
    this.activate(this.data.bSession)
    await setPassword(this.data.password)
    this.append("用户B密码初始化成功")
  },
  async bindBInvite() {
    if (!this.data.bSession) throw new Error("请先登录用户B")
    this.activate(this.data.bSession)
    const relation = await bindInvite({ inviteCode: this.data.inviteCode })
    this.append("邀请关系：" + relation.inviterUserId + " → " + relation.inviteeUserId + "，状态：" + relation.status)
  },
  async verifyA() {
    if (!this.data.aSession) throw new Error("请先登录用户A")
    this.activate(this.data.aSession)
    const [account, growthLogs] = await Promise.all([getGrowthAccount(), getGrowthLogs(1, 20)])
    const reward = growthLogs.records.find(item => item.bizType === "INVITE_USER_REGISTER")
    this.append("用户A成长值：" + account.experience + "；邀请注册流水：" + (reward ? reward.pointDelta : "未找到"))
  },
  async runAll() {
    if (this.data.running) return
    this.setData({ running: true, logs: [] })
    try {
      await this.loginA()
      await this.loginB()
      await this.setBPassword()
      await this.bindBInvite()
      await this.verifyA()
      wx.showToast({ title: "闭环验证完成", icon: "success" })
    } catch (error) {
      const message = error instanceof Error ? error.message : "联调失败"
      this.append("失败：" + message)
      wx.showToast({ title: message, icon: "none" })
    } finally {
      this.setData({ running: false })
    }
  }
})

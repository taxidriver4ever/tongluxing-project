import { JoinApplication, getJoinApplications, reviewJoinApplication } from "../../../api/chat"
Page({
  data: { loading: true, requests: [] as JoinApplication[] },
  onShow() { void this.load() },
  async load() { this.setData({ loading: true }); try { this.setData({ requests: await getJoinApplications("PENDING") }) } catch (error) { wx.showToast({ title: error instanceof Error ? error.message : "申请加载失败", icon: "none" }) } finally { this.setData({ loading: false }) } },
  async review(event: WechatMiniprogram.TouchEvent) { const id = String(event.currentTarget.dataset.id); const decision = String(event.currentTarget.dataset.decision) as "APPROVED" | "REJECTED"; try { await reviewJoinApplication(id, decision); this.setData({ requests: this.data.requests.filter(item => item.applicationId !== id) }); wx.showToast({ title: decision === "APPROVED" ? "已同意入队" : "已拒绝申请", icon: "none" }) } catch (error) { wx.showToast({ title: error instanceof Error ? error.message : "处理失败", icon: "none" }) } }
})

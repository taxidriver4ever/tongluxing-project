import {
  CreationDraft, CreationWaypoint, CreationWaypointType, TripLocation,
  addCreationWaypoint, createCreationDraft, deleteCreationWaypoint,
  getCreationDraft, getCreationDrafts, planCreationRoute, publishCreationDraft,
  reorderCreationWaypoints, saveCreationDraft, updateCreationWaypoint
} from "../../../api/trip"

const DRAFT_KEY = "current_trip_creation_draft_id"
const TYPE_VALUES: CreationWaypointType[] = ["MEETING", "REST", "HOTEL", "CHECK_IN"]

Page({
  data: {
    loading: true, saving: false, publishing: false, step: 1, draftId: "",
    title: "", description: "", date: "2026-08-01", time: "08:00", expectPeople: 5, durationDays: 1,
    startLocation: null as TripLocation | null, destination: null as TripLocation | null,
    waypoints: [] as CreationWaypoint[], route: null as CreationDraft["route"] | null,
    routeDistanceText: "--", routeDurationText: "--",
    waypointTypeIndex: 1, waypointTypeLabels: ["集合点", "休息点", "住宿点", "打卡点"],
    waypointTypes: TYPE_VALUES, stepLabels: ["基础信息", "规划路线", "添加经停点", "确认发布"]
  },
  async onLoad(options: Record<string, string>) {
    try {
      let draftId = options.draftId || String(wx.getStorageSync(DRAFT_KEY) || ""), draft: CreationDraft | undefined
      if (draftId) try { draft = await getCreationDraft(draftId) } catch (_) { draftId = "" }
      if (!draft) { const drafts = await getCreationDrafts("DRAFT"); draft = drafts[0] || await createCreationDraft(); draftId = draft.draftId }
      wx.setStorageSync(DRAFT_KEY, draftId); this.applyDraft(draft)
    } catch (error) { wx.showToast({ title: this.message(error), icon: "none" }) }
    finally { this.setData({ loading: false }) }
  },
  applyDraft(draft: CreationDraft) {
    const parts = (draft.startTime || "2026-08-01 08:00:00").split(" ")
    this.setData({ draftId: draft.draftId, title: draft.title || "", description: draft.description || "",
      date: parts[0], time: (parts[1] || "08:00").slice(0, 5), expectPeople: draft.expectPeople || 5,
      durationDays: draft.durationDays || 1, startLocation: draft.startLocation || null,
      destination: draft.destination || null, waypoints: draft.waypoints || [], route: draft.route || null })
    this.applyRouteText(draft.route)
  },
  applyRouteText(route?: CreationDraft["route"] | null) {
    this.setData({ routeDistanceText: route ? Math.round(route.totalDistance / 1000) + " km" : "--",
      routeDurationText: route ? (route.estimatedDuration / 3600).toFixed(1) + " h" : "--" })
  },
  onTitle(e: WechatMiniprogram.Input) { this.setData({ title: e.detail.value }) },
  onDescription(e: WechatMiniprogram.Input) { this.setData({ description: e.detail.value }) },
  onDate(e: any) { this.setData({ date: e.detail.value }) }, onTime(e: any) { this.setData({ time: e.detail.value }) },
  onWaypointType(e: any) { this.setData({ waypointTypeIndex: Number(e.detail.value) }) },
  minusPeople() { this.setData({ expectPeople: Math.max(1, this.data.expectPeople - 1) }) },
  plusPeople() { this.setData({ expectPeople: Math.min(20, this.data.expectPeople + 1) }) },
  minusDays() { this.setData({ durationDays: Math.max(1, this.data.durationDays - 1) }) },
  plusDays() { this.setData({ durationDays: Math.min(365, this.data.durationDays + 1) }) },
  chooseLocation(e: WechatMiniprogram.TouchEvent) {
    const target = String(e.currentTarget.dataset.target)
    wx.chooseLocation({ success: location => { const value: TripLocation = { name: location.name || location.address,
      address: location.address, latitude: Number(location.latitude), longitude: Number(location.longitude) }; this.setData({ [target]: value, route: null }); this.applyRouteText(null) },
      fail: error => { if (error.errMsg.indexOf("cancel") < 0) wx.showToast({ title: "地点选择失败", icon: "none" }) } })
  },
  async saveBasics(showToast = true): Promise<boolean> {
    if (!this.data.title.trim()) { wx.showToast({ title: "请填写行程标题", icon: "none" }); return false }
    this.setData({ saving: true })
    try { const draft = await saveCreationDraft(this.data.draftId, { title: this.data.title.trim(),
      description: this.data.description.trim(), startTime: this.data.date + " " + this.data.time + ":00",
      startLocation: this.data.startLocation || undefined, destination: this.data.destination || undefined,
      expectPeople: this.data.expectPeople, durationDays: this.data.durationDays }); this.applyDraft(draft)
      if (showToast) wx.showToast({ title: "草稿已保存", icon: "success" }); return true
    } catch (error) { wx.showToast({ title: this.message(error), icon: "none" }); return false }
    finally { this.setData({ saving: false }) }
  },
  async next() { if (this.data.step === 1 && !await this.saveBasics(false)) return
    if (this.data.step === 2 && !this.data.route) { wx.showToast({ title: "请先生成路线", icon: "none" }); return }
    if (this.data.step < 4) this.setData({ step: this.data.step + 1 }) },
  previous() { if (this.data.step > 1) this.setData({ step: this.data.step - 1 }) },
  selectStep(e: WechatMiniprogram.TouchEvent) { const step = Number(e.currentTarget.dataset.step); if (step <= this.data.step) this.setData({ step }) },
  async planRoute() { if (!this.data.startLocation || !this.data.destination) { wx.showToast({ title: "请先选择起点和终点", icon: "none" }); return }
    if (!await this.saveBasics(false)) return; wx.showLoading({ title: "正在规划" })
    try { const route = await planCreationRoute(this.data.draftId); this.setData({ route }); this.applyRouteText(route) }
    catch (error) { wx.showToast({ title: this.message(error), icon: "none" }) } finally { wx.hideLoading() } },
  addWaypoint() { if (this.data.waypoints.length >= 5) { wx.showToast({ title: "最多添加 5 个经停点", icon: "none" }); return }
    wx.chooseLocation({ success: async location => { try { await addCreationWaypoint(this.data.draftId, {
      name: location.name || location.address, address: location.address, latitude: Number(location.latitude), longitude: Number(location.longitude),
      type: TYPE_VALUES[this.data.waypointTypeIndex], sort: this.data.waypoints.length + 1, stayMinutes: 30 }); await this.refreshDraft()
    } catch (error) { wx.showToast({ title: this.message(error), icon: "none" }) } } }) },
  async changeWaypointType(e: any) { const index = Number(e.currentTarget.dataset.index), waypoint = this.data.waypoints[index]
    try { await updateCreationWaypoint(this.data.draftId, waypoint.waypointId, { name: waypoint.name, address: waypoint.address,
      latitude: waypoint.latitude, longitude: waypoint.longitude, type: TYPE_VALUES[Number(e.detail.value)],
      sort: waypoint.sort, stayMinutes: waypoint.stayMinutes }); await this.refreshDraft()
    } catch (error) { wx.showToast({ title: this.message(error), icon: "none" }) } },
  async removeWaypoint(e: WechatMiniprogram.TouchEvent) { try { await deleteCreationWaypoint(this.data.draftId, String(e.currentTarget.dataset.id)); await this.refreshDraft() }
    catch (error) { wx.showToast({ title: this.message(error), icon: "none" }) } },
  async moveWaypoint(e: WechatMiniprogram.TouchEvent) { const from = Number(e.currentTarget.dataset.index), to = from + Number(e.currentTarget.dataset.direction)
    if (to < 0 || to >= this.data.waypoints.length) return; const values = this.data.waypoints.slice(), current = values[from]; values[from] = values[to]; values[to] = current
    try { this.setData({ waypoints: await reorderCreationWaypoints(this.data.draftId, values.map(item => item.waypointId)), route: null }); this.applyRouteText(null) }
    catch (error) { wx.showToast({ title: this.message(error), icon: "none" }) } },
  async refreshDraft() { this.applyDraft(await getCreationDraft(this.data.draftId)) },
  async publish() { if (!this.data.route || this.data.route.status !== "VALID") { wx.showToast({ title: "请重新生成有效路线", icon: "none" }); return }
    this.setData({ publishing: true }); try { const result = await publishCreationDraft(this.data.draftId); wx.removeStorageSync(DRAFT_KEY)
      wx.showToast({ title: "行程发布成功", icon: "success" }); setTimeout(() => wx.redirectTo({ url: "/pages/trip/detail/detail?tripId=" + result.tripId }), 700)
    } catch (error) { wx.showToast({ title: this.message(error), icon: "none" }) } finally { this.setData({ publishing: false }) } },
  back() { wx.navigateBack({ fail: () => wx.switchTab({ url: "/pages/trip/trip" }) }) },
  message(error: unknown) { return error instanceof Error ? error.message : "操作失败，请稍后重试" }
})

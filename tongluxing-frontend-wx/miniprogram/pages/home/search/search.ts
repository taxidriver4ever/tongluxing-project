import { LocationSearchItem, getLocationHistory, resolveLocation, searchLocations, clearLocationHistory } from "../../../api/map"
let timer: number | undefined
Page({
  data: { keyword: "", loading: true, results: [] as LocationSearchItem[], history: [] as LocationSearchItem[], latitude: undefined as number | undefined, longitude: undefined as number | undefined },
  onLoad() { void this.initialize() },
  onUnload() { if (timer !== undefined) clearTimeout(timer) },
  async initialize() {
    let latitude: number | undefined; let longitude: number | undefined
    try { const location = await wx.getLocation({ type: "gcj02" }); latitude = location.latitude; longitude = location.longitude } catch (_error) {}
    this.setData({ latitude, longitude })
    await Promise.all([this.loadHistory(), this.search("")])
  },
  async loadHistory() { try { this.setData({ history: await getLocationHistory(this.data.latitude, this.data.longitude) }) } catch (_error) {} },
  onInput(event: WechatMiniprogram.Input) {
    const keyword = event.detail.value
    this.setData({ keyword })
    if (timer !== undefined) clearTimeout(timer)
    timer = setTimeout(() => void this.search(keyword.trim()), 260) as unknown as number
  },
  async search(keyword: string) {
    this.setData({ loading: true })
    try { this.setData({ results: await searchLocations(keyword, this.data.latitude, this.data.longitude) }) }
    catch (error) { wx.showToast({ title: error instanceof Error ? error.message : "地点搜索失败", icon: "none" }) }
    finally { this.setData({ loading: false }) }
  },
  async choose(event: WechatMiniprogram.TouchEvent) {
    const index = Number(event.currentTarget.dataset.index)
    const source = String(event.currentTarget.dataset.source) === "history" ? this.data.history : this.data.results
    const item = source[index]; if (!item) return
    try {
      const location = await resolveLocation({ name: item.name, address: item.address, latitude: item.latitude, longitude: item.longitude })
      wx.setStorageSync("selected_location", location)
      const channel = this.getOpenerEventChannel(); channel.emit("locationSelected", location)
      wx.navigateBack({ fail: () => wx.reLaunch({ url: "/pages/index/index" }) })
    } catch (error) { wx.showToast({ title: error instanceof Error ? error.message : "地点选择失败", icon: "none" }) }
  },
  async clearHistory() { try { await clearLocationHistory(); this.setData({ history: [] }) } catch (error) { wx.showToast({ title: error instanceof Error ? error.message : "清除失败", icon: "none" }) } }
})

const DEVICE_ID_KEY = "tongluxing_mini_device_id"

export function getDeviceId(): string {
  const saved = String(wx.getStorageSync(DEVICE_ID_KEY) || "")
  if (saved) return saved
  const system = wx.getSystemInfoSync()
  const random = Math.random().toString(36).slice(2, 10)
  const deviceId = ["wx", system.platform || "unknown", Date.now(), random].join("-")
  wx.setStorageSync(DEVICE_ID_KEY, deviceId)
  return deviceId
}

export function promptDownloadApp(action: string = "开启并执行行程"): void {
  wx.showModal({
    title: "请下载同路行 App",
    content: "微信小程序用于行前发布、招募和沟通。" + action + "需要在同路行 App 中完成。",
    confirmText: "我知道了",
    showCancel: false
  })
}

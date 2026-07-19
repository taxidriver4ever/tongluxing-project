export interface NavigationLayout {
  contentTop: number
  statusBarHeight: number
}

export function getNavigationLayout(): NavigationLayout {
  try {
    const system = wx.getSystemInfoSync()
    const menu = wx.getMenuButtonBoundingClientRect()
    const statusBarHeight = system.statusBarHeight || 24
    return {
      statusBarHeight,
      contentTop: Math.max(menu.bottom + 10, statusBarHeight + 48)
    }
  } catch (_) {
    return { statusBarHeight: 24, contentTop: 82 }
  }
}

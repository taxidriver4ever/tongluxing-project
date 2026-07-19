Page({ message() { wx.navigateTo({ url: "/pages/chat/session/session?id=CHAT02" }) }, friend() { wx.showToast({ title: "好友申请已发送", icon: "success" }) } })

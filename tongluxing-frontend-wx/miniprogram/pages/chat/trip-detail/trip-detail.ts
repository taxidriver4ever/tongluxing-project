import { CoreTrip, getMockTrip } from "../../../data/mock-core"

Page({ data: { trip: {} as CoreTrip }, onLoad() { getMockTrip("T1001").then(trip => this.setData({ trip })) }, open() { wx.navigateTo({ url: "/pages/trip/detail/detail?tripId=T1001" }) } })

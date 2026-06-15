import { getCurrentUserProfile, getIdentityStatus, UserProfile } from "../../api/user"
import { getMyVehicles, Vehicle } from "../../api/vehicle"
import { getToken } from "../../utils/auth-storage"

function defaultProfile(): UserProfile {
  return {
    userId: 0,
    nickname: "同道车友",
    avatarImageKey: "",
    gender: 0,
    birthday: "",
    cityCode: "",
    cityName: "未设置城市",
    bio: "",
    profileCompletion: 0,
    realNameStatus: "UNSUBMITTED"
  }
}

Component({
  data: {
    loading: false,
    profile: defaultProfile(),
    vehicles: [] as Vehicle[],
    defaultVehicle: null as Vehicle | null,
    defaultVehicleTitle: "添加我的第一辆车",
    defaultVehicleMeta: "填写品牌、车型、车牌和车辆照片",
    defaultVehicleStatusText: "",
    defaultVehicleStatusClass: "",
    headVehicleStatusText: "未添加车辆",
    headVehicleStatusClass: "",
    avatarText: "同",
    identityStatus: "UNSUBMITTED",
    rejectReason: "",
    headStyle: "",
    menus: [
      { label: "编辑个人资料", value: "头像、昵称、城市、简介", url: "/pages/profile-edit/profile-edit" },
      { label: "实名认证", value: "提交真实姓名和证件信息", url: "/pages/identity-cert/identity-cert" },
      { label: "我的车辆", value: "车辆档案、默认车辆、认证状态", url: "/pages/vehicle/list/list" },
      { label: "车辆认证", value: "行驶证上传和审核结果", url: "/pages/vehicle/list/list" },
      { label: "隐私设置", value: "资料、行程、位置可见范围", url: "/pages/privacy/privacy" },
      { label: "紧急联系人", value: "预留家人或朋友联系方式", url: "/pages/emergency-contact/emergency-contact" },
      { label: "公开资料卡", value: "预览推荐列表展示效果", url: "/pages/public-profile/public-profile" },
      { label: "商家工作台", value: "待办与收入", url: "/pages/merchant/home/home" },
      { label: "帮助与客服", value: "微信客服" }
    ]
  },
  lifetimes: {
    attached() {
      this.initProfileSafeArea()
    }
  },
  pageLifetimes: {
    show() {
      this.loadProfile()
    }
  },
  methods: {
    initProfileSafeArea() {
      try {
        const system = wx.getSystemInfoSync()
        const menu = wx.getMenuButtonBoundingClientRect()
        const top = menu.top || system.statusBarHeight || 24
        const rightSafe = system.windowWidth - menu.left + 8
        this.setData({
          headStyle: "padding-top: " + top + "px; padding-right: " + rightSafe + "px;"
        })
      } catch (error) {
        this.setData({
          headStyle: "padding-top: 36px; padding-right: 112px;"
        })
      }
    },
    async loadProfile() {
      if (!getToken()) {
        wx.showToast({ title: "请先登录", icon: "none" })
        return
      }

      this.setData({ loading: true })
      try {
        const profile = await getCurrentUserProfile()
        const identity = await getIdentityStatus()
        const vehicleList = await getMyVehicles()
        const vehicles = vehicleList.vehicles || []
        const defaultVehicle = this.findDefaultVehicle(vehicles)
        this.setData({
          profile,
          vehicles,
          defaultVehicle,
          defaultVehicleTitle: this.getVehicleTitle(defaultVehicle),
          defaultVehicleMeta: this.getVehicleMeta(defaultVehicle),
          defaultVehicleStatusText: this.getVehicleStatusText(defaultVehicle),
          defaultVehicleStatusClass: this.getVehicleStatusClass(defaultVehicle),
          headVehicleStatusText: this.getHeadVehicleStatusText(defaultVehicle),
          headVehicleStatusClass: this.getVehicleStatusClass(defaultVehicle),
          avatarText: profile.nickname ? profile.nickname.substring(0, 1) : "同",
          identityStatus: identity.status || profile.realNameStatus,
          rejectReason: identity.rejectReason || ""
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    onMenuTap(event: WechatMiniprogram.TouchEvent) {
      const url = event.currentTarget.dataset.url
      if (url) {
        wx.navigateTo({ url })
        return
      }
      wx.showToast({ title: "客服入口待接入", icon: "none" })
    },
    goVehicleEdit() {
      const vehicle = this.data.defaultVehicle
      if (vehicle && vehicle.vehicleId) {
        wx.navigateTo({ url: "/pages/vehicle/edit/edit?vehicleId=" + vehicle.vehicleId })
        return
      }
      wx.navigateTo({ url: "/pages/vehicle/edit/edit" })
    },
    goVehicleList() {
      wx.navigateTo({ url: "/pages/vehicle/list/list" })
    },
    goVehicleCertification() {
      const vehicle = this.data.defaultVehicle
      if (vehicle && vehicle.vehicleId) {
        wx.navigateTo({ url: "/pages/vehicle/certification/certification?vehicleId=" + vehicle.vehicleId })
        return
      }
      wx.navigateTo({ url: "/pages/vehicle/list/list" })
    },
    findDefaultVehicle(vehicles: Vehicle[]): Vehicle | null {
      if (!vehicles.length) {
        return null
      }
      for (let index = 0; index < vehicles.length; index += 1) {
        if (vehicles[index].isDefault) {
          return vehicles[index]
        }
      }
      return vehicles[0]
    },
    getVehicleTitle(vehicle: Vehicle | null): string {
      if (!vehicle) {
        return "添加我的第一辆车"
      }
      return (vehicle.brand || "未填写品牌") + " " + (vehicle.model || "")
    },
    getVehicleMeta(vehicle: Vehicle | null): string {
      if (!vehicle) {
        return "填写品牌、车型、车牌和车辆照片"
      }
      return (vehicle.plateNoMask || "未填写车牌") + " · " + (vehicle.vehicleType || "车辆类型") + " · " + (vehicle.energyType || "能源未填")
    },
    getVehicleStatusText(vehicle: Vehicle | null): string {
      if (!vehicle) {
        return ""
      }
      if (vehicle.certificationStatus === "APPROVED") {
        return "已认证"
      }
      if (vehicle.certificationStatus === "PENDING") {
        return "审核中"
      }
      if (vehicle.certificationStatus === "REJECTED") {
        return "认证被拒"
      }
      return "未认证"
    },
    getHeadVehicleStatusText(vehicle: Vehicle | null): string {
      if (!vehicle) {
        return "未添加车辆"
      }
      if (vehicle.certificationStatus === "APPROVED") {
        return "车辆已认证"
      }
      if (vehicle.certificationStatus === "PENDING") {
        return "车辆审核中"
      }
      if (vehicle.certificationStatus === "REJECTED") {
        return "车辆被拒"
      }
      return "车辆待认证"
    },
    getVehicleStatusClass(vehicle: Vehicle | null): string {
      if (!vehicle) {
        return ""
      }
      if (vehicle.certificationStatus === "APPROVED") {
        return "green"
      }
      if (vehicle.certificationStatus === "PENDING") {
        return "orange"
      }
      if (vehicle.certificationStatus === "REJECTED") {
        return "red"
      }
      return ""
    },
    goLogin() {
      wx.navigateTo({ url: "/pages/login/login" })
    },
    goGroupbuy() {
      wx.navigateTo({ url: "/pages/groupbuy/list/list" })
    },
    goVerification() {
      wx.navigateTo({ url: "/pages/order/verification-code/verification-code" })
    },
    showTradeTip(event: WechatMiniprogram.TouchEvent) {
      const label = String(event.currentTarget.dataset.label || "订单")
      wx.showToast({ title: label + "功能待接入", icon: "none" })
    },
    identityText(status: string): string {
      if (status === "APPROVED") {
        return "已实名"
      }
      if (status === "PENDING") {
        return "审核中"
      }
      if (status === "REJECTED") {
        return "实名被拒"
      }
      return "未实名"
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})

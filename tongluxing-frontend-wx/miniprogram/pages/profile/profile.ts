import { logout } from "../../api/auth"
import { getBadgeWall } from "../../api/growth"
import { getCurrentUserProfile, UserProfile } from "../../api/user"
import { clearSession, getUserId } from "../../utils/auth-storage"
import { getNavigationLayout } from "../../utils/navigation-layout"
import { getImageUrl } from "../../api/storage"

Page({
 data:{contentTop:82,loading:true,profile:null as UserProfile|null,badgeCount:0,avatarUrl:""},
 onLoad(){this.setData({contentTop:getNavigationLayout().contentTop})}, onShow(){void this.load()},
 async load(){this.setData({loading:true});try{const [profile,badges]=await Promise.all([getCurrentUserProfile(),getBadgeWall()]);let avatarUrl="";if(profile.avatarImageKey){try{avatarUrl=await getImageUrl(profile.avatarImageKey)}catch(_error){avatarUrl=""}}this.setData({profile,badgeCount:badges.earned.length,avatarUrl})}catch(error){wx.showToast({title:error instanceof Error?error.message:"个人资料加载失败",icon:"none"})}finally{this.setData({loading:false})}},
 goPublic(){wx.navigateTo({url:"/pages/public-profile/public-profile?userId="+getUserId()})}, goEdit(){wx.navigateTo({url:"/pages/profile-edit/profile-edit"})}, goSettings(){wx.navigateTo({url:"/pages/profile/settings/settings"})}, goLicense(){wx.navigateTo({url:"/pages/identity-cert/identity-cert"})}, goVehicle(){wx.navigateTo({url:"/pages/vehicle/list/list"})}, goBadges(){wx.navigateTo({url:"/pages/profile/badges/badges"})}, goInvite(){wx.navigateTo({url:"/pages/profile/invite/invite"})}, fillInvite(){wx.navigateTo({url:"/pages/login/scan-invite/scan-invite?mode=profile"})}, goCertification(){wx.navigateTo({url:"/pages/identity-cert/identity-cert"})}, goSupport(){wx.navigateTo({url:"/pages/customer-support/customer-support"})},
 async signOut(){const result=await wx.showModal({title:"退出登录",content:"确定退出当前账号吗？"});if(!result.confirm)return;try{await logout()}catch(_error){}clearSession();wx.reLaunch({url:"/pages/login/login"})}
})

import { Team, applyTeam, getTeam } from "../../../api/team"
import { getMyVehicles } from "../../../api/vehicle"
import { getUserId } from "../../../utils/auth-storage"
Page({
 data:{safeTop:44,loading:true,team:null as Team|null,isOwner:false},
 onLoad(options:Record<string,string>){try{this.setData({safeTop:wx.getSystemInfoSync().statusBarHeight||44})}catch(_error){}void this.load(String(options.teamId||""))},
 async load(teamId:string){if(!teamId){wx.showToast({title:"缺少车队编号",icon:"none"});return}try{const team=await getTeam(teamId);this.setData({team,isOwner:team.ownerUserId===String(getUserId())})}catch(error){wx.showToast({title:error instanceof Error?error.message:"车队加载失败",icon:"none"})}finally{this.setData({loading:false})}},
 back(){wx.navigateBack({fail:()=>wx.reLaunch({url:"/pages/index/index"})})},route(){if(this.data.team)wx.navigateTo({url:"/pages/trip/detail/detail?tripId="+this.data.team.tripId})},members(){if(this.data.team)wx.navigateTo({url:"/pages/team/members/members?teamId="+this.data.team.teamId})},chat(){const team=this.data.team;if(!team||!team.chatConversationId){wx.showToast({title:"该车队暂未创建群聊",icon:"none"});return}wx.navigateTo({url:"/pages/chat/session/session?id="+team.chatConversationId})},manage(){wx.navigateTo({url:"/pages/team/manage/manage"})},
 async join(){const team=this.data.team;if(!team)return;try{const vehicles=await getMyVehicles();const vehicle=vehicles.vehicles.find(item=>item.isDefault)||vehicles.vehicles[0];await applyTeam(team.teamId,{applicantVehicleId:vehicle?vehicle.vehicleId:undefined,applyMessage:"路线很合适，希望加入车队一起出发",joinQuestionJson:"{}"});wx.showToast({title:"申请已提交",icon:"success"})}catch(error){wx.showToast({title:error instanceof Error?error.message:"申请失败",icon:"none"})}}
})

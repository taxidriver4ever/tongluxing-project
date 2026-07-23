import { Trip, getMyTrips } from "../../../api/trip"
interface TripView extends Trip { statusText: string; routeText: string; metaText: string }
function view(item: Trip): TripView { const status = ({ PUBLISHED:"招募中", READY:"待出发", ONGOING:"进行中", ENDED:"已完成", SETTLED:"已结算", CANCELLED:"已取消" } as Record<string,string>)[item.status] || item.status; const distance = Math.round((item.routeDistance || item.totalDistanceMeters || 0)/1000); return { ...item, statusText: status, routeText: (item.startLocation?.name || item.startName) + " → " + (item.endLocation?.name || item.endName), metaText: (item.departureTime || "时间待定").replace("T"," ").slice(0,16) + " · " + distance + "km · " + (item.joinedVehicleCount || 1) + "/" + (item.maxVehicleCount || item.expectedPeople || 1) + "人" } }
Page({
  data: { active: "all", loading: true, filters: [{key:"all",label:"全部"},{key:"recruiting",label:"招募中"},{key:"ongoing",label:"进行中"}], trips: [] as TripView[], visibleTrips: [] as TripView[] },
  onShow() { void this.load() },
  async load() { this.setData({loading:true}); try { const result=await getMyTrips("active"); const trips=result.trips.map(view); this.setData({trips,visibleTrips:this.filter(trips,this.data.active)}) } catch(error){wx.showToast({title:error instanceof Error?error.message:"行程加载失败",icon:"none"})} finally{this.setData({loading:false})} },
  filter(rows: TripView[], active: string): TripView[] { if(active==="all") return rows; if(active==="recruiting") return rows.filter(item=>["PUBLISHED","READY"].includes(item.status)); return rows.filter(item=>item.status==="ONGOING") },
  changeFilter(event:WechatMiniprogram.TouchEvent){const active=String(event.currentTarget.dataset.key);this.setData({active,visibleTrips:this.filter(this.data.trips,active)})},
  goDetail(event:WechatMiniprogram.TouchEvent){wx.navigateTo({url:"/pages/trip/detail/detail?tripId="+event.currentTarget.dataset.id})}
})

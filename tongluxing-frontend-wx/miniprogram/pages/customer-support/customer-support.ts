import { createTicket, getMyNotifications, getMyTickets, markNotificationRead, Notice, Ticket } from "../../api/customer-service"

function statusText(value:string):string{return ({OPEN:"待领取",PROCESSING:"处理中",CLOSED:"已完成"} as Record<string,string>)[value]||value||"处理中"}

Page({
  data:{loading:true,tickets:[] as (Ticket&{statusText:string})[],notices:[] as Notice[]},
  onShow(){void this.load()},
  async load(){
    this.setData({loading:true})
    try{
      const [tickets,notices]=await Promise.all([getMyTickets(),getMyNotifications()])
      this.setData({tickets:tickets.records.map(item=>({...item,statusText:statusText(item.ticketStatus)})),notices:notices.records})
    }catch(error){wx.showToast({title:error instanceof Error?error.message:"加载失败",icon:"none"})}
    finally{this.setData({loading:false})}
  },
  submit(event:WechatMiniprogram.TouchEvent){
    const type=String(event.currentTarget.dataset.type)==="COMPLAINT"?"COMPLAINT":"TICKET"
    wx.showModal({title:type==="COMPLAINT"?"提交投诉":"联系客服",editable:true,placeholderText:"请详细描述遇到的问题",success:async result=>{
      const content=(result.content||"").trim();if(!result.confirm||!content)return
      try{await createTicket(type,type==="COMPLAINT"?"用户投诉":"客服咨询",content);wx.showToast({title:"工单已提交",icon:"success"});void this.load()}
      catch(error){wx.showToast({title:error instanceof Error?error.message:"提交失败",icon:"none"})}
    }})
  },
  async readNotice(event:WechatMiniprogram.TouchEvent){
    const id=Number(event.currentTarget.dataset.id),status=String(event.currentTarget.dataset.status||"")
    if(!id||status==="READ")return
    try{await markNotificationRead(id);await this.load()}catch(error){wx.showToast({title:error instanceof Error?error.message:"操作失败",icon:"none"})}
  }
})

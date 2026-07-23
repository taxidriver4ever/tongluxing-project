import { Conversation, ConversationMember, ConversationSettings, exitConversation, getConversationMembers, getConversationSettings, getConversations, updateConversationSettings } from "../../../api/chat"
Page({
  data:{conversationId:"",tripId:"",loading:true,conversation:null as Conversation|null,members:[] as ConversationMember[],settings:{muted:false,pinned:false} as ConversationSettings},
  onLoad(options:Record<string,string>){this.setData({conversationId:String(options.id||""),tripId:String(options.tripId||"")});void this.load()},
  async load(){if(!this.data.conversationId)return;try{const [list,members,settings]=await Promise.all([getConversations(),getConversationMembers(this.data.conversationId),getConversationSettings(this.data.conversationId)]);const conversation=list.conversations.find(item=>item.conversationId===this.data.conversationId)||null;this.setData({conversation,members,settings,tripId:this.data.tripId||(conversation?conversation.bizId:"")})}catch(error){wx.showToast({title:error instanceof Error?error.message:"群聊信息加载失败",icon:"none"})}finally{this.setData({loading:false})}},
  members(){wx.navigateTo({url:"/pages/team/members/members?conversationId="+this.data.conversationId})},
  trip(){if(!this.data.tripId){wx.showToast({title:"该会话暂未关联行程",icon:"none"});return}wx.navigateTo({url:"/pages/chat/trip-detail/trip-detail?tripId="+this.data.tripId})},
  sharedLocation(){wx.showToast({title:"行中实时位置请在 App 查看",icon:"none"})},
  search(){wx.showToast({title:"可在会话页下拉加载历史消息",icon:"none"})},
  async toggle(event:WechatMiniprogram.SwitchChange){const key=String(event.currentTarget.dataset.key);const settings={...this.data.settings,[key]:event.detail.value};try{const saved=await updateConversationSettings(this.data.conversationId,{muted:Boolean(settings.muted),pinned:Boolean(settings.pinned)});this.setData({settings:saved})}catch(error){wx.showToast({title:error instanceof Error?error.message:"设置失败",icon:"none"});await this.load()}},
  async exit(){const result=await wx.showModal({title:"退出群聊",content:"退出后将无法继续查看该群聊消息，确认继续吗？",confirmColor:"#FF3B30"});if(!result.confirm)return;try{await exitConversation(this.data.conversationId);wx.showToast({title:"已退出群聊",icon:"success"});setTimeout(()=>wx.reLaunch({url:"/pages/message/message"}),450)}catch(error){wx.showToast({title:error instanceof Error?error.message:"退出失败",icon:"none"})}}
})

import { request } from "../utils/request"

export interface TicketMessage { id:number; senderType:string; senderId:number; content:string; createdAt:string }
export interface Ticket { id:number; scene:string; title:string; content:string; ticketStatus:string; priority:string; createdAt:string; updatedAt:string; messages:TicketMessage[] }
export interface Notice { id:number; scene:string; eventType:string; title:string; content:string; readStatus:string; createdAt:string }
interface PageResult<T> { records:T[]; total:number; page:number; size:number }

export function createTicket(type:"TICKET"|"COMPLAINT",title:string,content:string):Promise<Ticket>{
  return request<Ticket>(type==="COMPLAINT"?"/v1/customer-service/complaints":"/v1/customer-service/tickets",{
    method:"POST",
    data:{scene:type==="COMPLAINT"?"COMPLAINT":"GENERAL",targetType:"",targetId:"",title,content,imageKeys:[],requestId:"WX-CS-"+Date.now()+"-"+Math.random().toString(36).slice(2,8)}
  })
}
export function getMyTickets():Promise<PageResult<Ticket>>{return request<PageResult<Ticket>>("/v1/customer-service/tickets/me?page=1&size=30")}
export function getMyNotifications():Promise<PageResult<Notice>>{return request<PageResult<Notice>>("/v1/notifications/me?scene=CUSTOMER_SERVICE&page=1&size=30")}
export function markNotificationRead(id:number):Promise<void>{return request<void>("/v1/notifications/"+id+"/read",{method:"POST"})}

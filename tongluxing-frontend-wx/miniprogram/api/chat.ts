import { request } from "../utils/request"

export interface Conversation {
  conversationId: string
  bizType: string
  bizId: string
  conversationName: string
  conversationStatus: string
  providerType: string
  lastMessagePreview: string
  lastMessageAt: string
  unreadCount?: number
  pinned: boolean
  muted: boolean
}
export interface ImUserSig { sdkAppId: number; userId: string; userSig: string; expireTime: number }
export interface ConversationList { conversations: Conversation[] }
export interface Message {
  messageId: string
  conversationId: string
  senderUserId: string
  messageType: string
  content: string
  senderNickname: string
  senderAvatarImageKey: string
  senderAvatarUrl: string
  payload: Record<string, unknown>
  messageStatus: string
  sentAt: string
}
export interface MessageList { messages: Message[] }
export interface ConversationSettings { conversationId?: string; muted: boolean; pinned: boolean }
export interface ConversationMember {
  memberId: string
  conversationId: string
  userId: string
  memberRole: string
  memberStatus: string
  unreadCount: number
  nickname: string
  avatarImageKey: string
  avatarUrl: string
  totalTripCount: number
  totalDistanceMeters: number
  completedWaypointCount: number
  muted: boolean
  pinned: boolean
  joinedAt: string
}
export interface JoinApplication {
  applicationId: string
  conversationId: string
  conversationName: string
  applicantUserId: string
  nickname: string
  avatarImageKey: string
  applicationMessage: string
  status: string
  createdAt: string
}
export function createTeamConversation(teamId: string, ownerUserId: string, conversationName: string): Promise<Conversation> {
  return request<Conversation>("/v1/chats/team-conversations", { method: "POST", data: { teamId: Number(teamId), ownerUserId: Number(ownerUserId), conversationName } })
}
export function getConversations(title = ""): Promise<ConversationList> {
  const suffix = title.trim() ? "?title=" + encodeURIComponent(title.trim()) : ""
  return request<ConversationList>("/v1/chats/conversations" + suffix)
}
export function getTripConversation(tripId: string): Promise<Conversation> { return request<Conversation>("/v1/chats/trips/" + tripId + "/conversation") }
export function getImUserSig(): Promise<ImUserSig> { return request<ImUserSig>("/v1/chats/im/user-sig") }
export function getMessages(conversationId: string, beforeMessageId = "", limit = 30): Promise<MessageList> {
  let url = "/v1/chats/conversations/" + conversationId + "/messages?limit=" + limit
  if (beforeMessageId) url += "&beforeMessageId=" + beforeMessageId
  return request<MessageList>(url)
}
export function sendMessage(conversationId: string, content: string): Promise<Message> {
  return request<Message>("/v1/chats/conversations/" + conversationId + "/messages", { method: "POST", data: { messageType: "TEXT", content } })
}
export function addConversationMember(conversationId: string, userId: string): Promise<ConversationMember> {
  return request<ConversationMember>("/v1/chats/conversations/" + conversationId + "/members", { method: "POST", data: { userId: Number(userId) } })
}
export function getConversationMembers(conversationId: string): Promise<ConversationMember[]> { return request<ConversationMember[]>("/v1/chats/conversations/" + conversationId + "/members") }
export function exitConversation(conversationId: string): Promise<void> { return request<void>("/v1/chats/conversations/" + conversationId + "/members/me", { method: "DELETE" }) }
export function getConversationSettings(conversationId: string): Promise<ConversationSettings> { return request<ConversationSettings>("/v1/chats/conversations/" + conversationId + "/settings") }
export function updateConversationSettings(conversationId: string, data: ConversationSettings): Promise<ConversationSettings> { return request<ConversationSettings>("/v1/chats/conversations/" + conversationId + "/settings", { method: "PUT", data }) }
export function applyToConversation(conversationId: string, message: string): Promise<JoinApplication> { return request<JoinApplication>("/v1/chats/conversations/" + conversationId + "/join-applications", { method: "POST", data: { message } }) }
export function getJoinApplications(status = "PENDING"): Promise<JoinApplication[]> { return request<JoinApplication[]>("/v1/chats/join-applications?status=" + encodeURIComponent(status)) }
export function reviewJoinApplication(applicationId: string, decision: "APPROVED" | "REJECTED"): Promise<JoinApplication> { return request<JoinApplication>("/v1/chats/join-applications/" + applicationId + "/review", { method: "POST", data: { decision } }) }

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
}

export interface ImUserSig {
  sdkAppId: number
  userId: string
  userSig: string
  expireTime: number
}

export interface ConversationList {
  conversations: Conversation[]
}

export interface Message {
  messageId: string
  conversationId: string
  senderUserId: string
  messageType: string
  content: string
  messageStatus: string
  sentAt: string
}

export interface MessageList {
  messages: Message[]
}

export function createTeamConversation(teamId: string, ownerUserId: string, conversationName: string): Promise<Conversation> {
  return request<Conversation>("/v1/chats/team-conversations", {
    method: "POST",
    data: {
      teamId: Number(teamId),
      ownerUserId: Number(ownerUserId),
      conversationName
    }
  })
}

export function getConversations(): Promise<ConversationList> {
  return request<ConversationList>("/v1/chats/conversations")
}

export function getImUserSig(): Promise<ImUserSig> {
  return request<ImUserSig>("/v1/chats/im/user-sig")
}

export function getMessages(conversationId: string, beforeMessageId: string, limit: number): Promise<MessageList> {
  let url = "/v1/chats/conversations/" + conversationId + "/messages?limit=" + limit
  if (beforeMessageId) {
    url += "&beforeMessageId=" + beforeMessageId
  }
  return request<MessageList>(url)
}

export function sendMessage(conversationId: string, content: string): Promise<Message> {
  return request<Message>("/v1/chats/conversations/" + conversationId + "/messages", {
    method: "POST",
    data: {
      messageType: "TEXT",
      content
    }
  })
}

export function addConversationMember(conversationId: string, userId: string): Promise<unknown> {
  return request<unknown>("/v1/chats/conversations/" + conversationId + "/members", {
    method: "POST",
    data: {
      userId: Number(userId)
    }
  })
}

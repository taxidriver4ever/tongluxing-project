import { getImUserSig, ImUserSig } from "../api/chat"

declare const require: any

export interface ImTextMessage {
  messageId: string
  senderUserId: string
  content: string
  sentAt: string
  self: boolean
}

let tim: any = null
let timSdk: any = null
let currentUserId = ""
let loggedIn = false
let messageHandler: any = null

function getTimSdk(): any {
  if (!timSdk) {
    timSdk = require("tim-wx-sdk")
  }
  return timSdk
}

function formatTime(value: number): string {
  if (!value) {
    return ""
  }
  const date = new Date(value * 1000)
  const hour = String(date.getHours()).padStart(2, "0")
  const minute = String(date.getMinutes()).padStart(2, "0")
  return hour + ":" + minute
}

function toTextMessage(message: any): ImTextMessage | null {
  if (!message || !message.payload || !message.payload.text) {
    return null
  }
  return {
    messageId: String(message.ID || message.id || message.time || ""),
    senderUserId: String(message.from || ""),
    content: String(message.payload.text || ""),
    sentAt: formatTime(Number(message.time || 0)),
    self: String(message.from || "") === currentUserId
  }
}

export async function loginTencentIm(): Promise<ImUserSig> {
  const sig = await getImUserSig()
  const TIM = getTimSdk()
  if (!tim) {
    tim = TIM.create({ SDKAppID: Number(sig.sdkAppId) })
    tim.setLogLevel(1)
  }
  if (!loggedIn || currentUserId !== sig.userId) {
    await tim.login({
      userID: sig.userId,
      userSig: sig.userSig
    })
    currentUserId = sig.userId
    loggedIn = true
  }
  return sig
}

export function onImTextMessage(callback: (message: ImTextMessage) => void): void {
  const TIM = getTimSdk()
  if (!tim) {
    return
  }
  offImTextMessage()
  messageHandler = (event: any) => {
    const list = event && event.data ? event.data : []
    list.forEach((item: any) => {
      const message = toTextMessage(item)
      if (message) {
        callback(message)
      }
    })
  }
  tim.on(TIM.EVENT.MESSAGE_RECEIVED, messageHandler)
}

export function offImTextMessage(): void {
  const TIM = getTimSdk()
  if (tim && messageHandler) {
    tim.off(TIM.EVENT.MESSAGE_RECEIVED, messageHandler)
    messageHandler = null
  }
}

export async function getGroupMessageList(groupId: string, nextReqMessageID: string): Promise<{ messages: ImTextMessage[]; nextReqMessageID: string; completed: boolean }> {
  const result = await tim.getMessageList({
    conversationID: "GROUP" + groupId,
    nextReqMessageID,
    count: 15
  })
  const rawMessages = result && result.data && result.data.messageList ? result.data.messageList : []
  const messages: ImTextMessage[] = []
  rawMessages.forEach((item: any) => {
    const message = toTextMessage(item)
    if (message) {
      messages.push(message)
    }
  })
  const nextId = result && result.data && result.data.nextReqMessageID ? result.data.nextReqMessageID : ""
  const completed = Boolean(result && result.data && result.data.isCompleted)
  return {
    messages,
    nextReqMessageID: nextId,
    completed
  }
}

export async function sendGroupTextMessage(groupId: string, text: string): Promise<ImTextMessage> {
  const TIM = getTimSdk()
  const message = tim.createTextMessage({
    to: groupId,
    conversationType: TIM.TYPES.CONV_GROUP,
    payload: {
      text
    }
  })
  const result = await tim.sendMessage(message)
  const sent = result && result.data && result.data.message ? result.data.message : message
  const converted = toTextMessage(sent)
  if (converted) {
    return converted
  }
  return {
    messageId: String(Date.now()),
    senderUserId: currentUserId,
    content: text,
    sentAt: formatTime(Math.floor(Date.now() / 1000)),
    self: true
  }
}

import { request } from "../utils/request"

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface InviteCode {
  inviteCode: string
  scene: string
  enabled: boolean
}

export interface InviteBindRequest {
  inviteCode: string
}

export interface InvitePhoneBindRequest {
  inviterPhone: string
}

export interface InviteBindResult {
  inviterUserId: number
  inviteeUserId: number
  status: string
  boundAt: string
}

export interface InvitationRecord {
  relationId: number
  inviteeUserId: number
  inviteCode: string
  status: string
  boundAt: string
  firstTeamCompletedAt: string | null
}

export interface InviteRewardProgress {
  validInviteCount: number
  nextRewardNeed: number
  grantedRuleCodes: string[]
}

export function getMyInviteCode(): Promise<InviteCode> {
  return request<InviteCode>("/v1/invites/code")
}

export function bindInvite(data: InviteBindRequest): Promise<InviteBindResult> {
  return request<InviteBindResult>("/v1/invites/bind", {
    method: "POST",
    data
  })
}

export function bindInviteByPhone(data: InvitePhoneBindRequest): Promise<InviteBindResult> {
  return request<InviteBindResult>("/v1/invites/bind-by-phone", {
    method: "POST",
    data
  })
}

export function getInviteSummary(): Promise<InviteRewardProgress> {
  return request<InviteRewardProgress>("/v1/invites/me/summary")
}

export function getInvitationList(status?: string, page: number = 1, size: number = 20): Promise<PageResult<InvitationRecord>> {
  let url = "/v1/invites/me/records?page=" + page + "&size=" + size
  if (status) url += "&status=" + encodeURIComponent(status)
  return request<PageResult<InvitationRecord>>(url)
}

export function getInviteRewardProgress(): Promise<InviteRewardProgress> {
  return request<InviteRewardProgress>("/v1/invites/me/rewards")
}

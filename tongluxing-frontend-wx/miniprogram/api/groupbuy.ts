import { request } from "../utils/request"

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface GroupbuyParticipant {
  participantId: number
  activityId: number
  orderId: number
  userId: number
  participantStatus: string
  joinedAt: string
  paidAt: string
  refundedAt: string
}

export interface GroupbuyActivity {
  activityId: number
  merchantId: number
  productId: number
  initiatorUserId: number
  targetPeople: number
  currentPeople: number
  groupPrice: number
  activityStatus: string
  startAt: string
  expireAt: string
  successAt: string
  failedAt: string
  participants: GroupbuyParticipant[]
}

export interface CreateGroupbuyRequest {
  productId: number
  targetPeople: number
  validHours: number
  requestId: string
}

export function getGroupbuys(status?: string, page: number = 1, size: number = 20): Promise<PageResult<GroupbuyActivity>> {
  let url = "/v1/groupbuys?page=" + page + "&size=" + size
  if (status) url += "&status=" + encodeURIComponent(status)
  return request<PageResult<GroupbuyActivity>>(url)
}

export function getGroupbuy(activityId: number): Promise<GroupbuyActivity> {
  return request<GroupbuyActivity>("/v1/groupbuys/" + activityId)
}

export function createGroupbuy(data: CreateGroupbuyRequest): Promise<GroupbuyActivity> {
  return request<GroupbuyActivity>("/v1/groupbuys", {
    method: "POST",
    data
  })
}

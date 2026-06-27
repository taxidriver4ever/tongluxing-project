import { request } from "../utils/request"

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface CouponSummary {
  id: number
  templateId: number
  couponName: string
  couponType: string
  thresholdAmount: number
  discountAmount: number
  couponStatus: string
  validStartAt: string
  validEndAt: string
}

export interface UserCouponDetail {
  id: number
  templateId: number
  couponName: string
  couponType: string
  issuerId: number
  thresholdAmount: number
  discountAmount: number
  scopeJson: string
  couponStatus: string
  validStartAt: string
  validEndAt: string
  lockedOrderId: number
  usedOrderId: number
}

export interface AvailableCoupon {
  id: number
  couponName: string
  deductionAmount: number
  validEndAt: string
}

export interface CouponIssueResult {
  couponUserId: number
  status: string
  duplicate: boolean
}

export function getUserCoupons(status?: string, type?: string, page: number = 1, size: number = 20): Promise<PageResult<CouponSummary>> {
  let url = "/v1/coupons/me?page=" + page + "&size=" + size
  if (status) url += "&status=" + encodeURIComponent(status)
  if (type) url += "&type=" + encodeURIComponent(type)
  return request<PageResult<CouponSummary>>(url)
}

export function getUserCouponDetail(id: number): Promise<UserCouponDetail> {
  return request<UserCouponDetail>("/v1/coupons/me/" + id)
}

export function getAvailableCoupons(orderType: string, amount: number, merchantId?: number): Promise<AvailableCoupon[]> {
  let url = "/v1/coupons/available?orderType=" + encodeURIComponent(orderType) + "&amount=" + amount
  if (merchantId) url += "&merchantId=" + merchantId
  return request<AvailableCoupon[]>(url)
}

export function claimCoupon(templateId: number): Promise<CouponIssueResult> {
  return request<CouponIssueResult>("/v1/coupons/templates/" + templateId + "/claim", {
    method: "POST"
  })
}

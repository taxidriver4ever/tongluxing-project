import { request } from "../utils/request"

export interface JsapiPaymentRequest {
  orderId: number
  openId: string
}

export interface JsapiPayParams {
  appId: string
  timeStamp: string
  nonceStr: string
  packageValue: string
  signType: string
  paySign: string
}

export interface RefundApplyRequest {
  orderId: number
  reason: string
  requestId: string
}

export interface Refund {
  refundId: number
  orderId: number
  refundNo: string
  refundAmount: number
  refundStatus: string
  auditStatus: string
  requestedAt: string
  refundedAt: string
}

export function createJsapiPayment(data: JsapiPaymentRequest): Promise<JsapiPayParams> {
  return request<JsapiPayParams>("/v1/payments/jsapi", {
    method: "POST",
    data
  })
}

export function applyRefund(data: RefundApplyRequest): Promise<Refund> {
  return request<Refund>("/v1/payments/refunds", {
    method: "POST",
    data
  })
}

export function getRefund(refundId: number): Promise<Refund> {
  return request<Refund>("/v1/payments/refunds/" + refundId)
}

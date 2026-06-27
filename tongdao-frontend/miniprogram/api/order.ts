import { request } from "../utils/request"

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface OrderPreviewRequest {
  productId: number
  activityId?: number
  quantity: number
  userCouponId?: number
  useBestCoupon?: boolean
}

export interface CreateOrderRequest extends OrderPreviewRequest {
  remark?: string
  requestId: string
}

export interface OrderItem {
  itemId: number
  productId: number
  productName: string
  productType: string
  unitPrice: number
  quantity: number
  totalAmount: number
}

export interface OrderPreview {
  productId: number
  activityId: number
  originalAmount: number
  groupbuyDiscountAmount: number
  couponDeductionAmount: number
  payableAmount: number
  selectedCouponId: number
  priceDescription: string
}

export interface Order {
  orderId: number
  orderNo: string
  userId: number
  merchantId: number
  productId: number
  activityId: number
  orderStatus: string
  paymentStatus: string
  verificationStatus: string
  refundStatus: string
  profitSharingStatus: string
  originalAmount: number
  groupbuyDiscountAmount: number
  couponDeductionAmount: number
  payableAmount: number
  paidAmount: number
  lockedCouponId: number
  expireAt: string
  paidAt: string
  completedAt: string
  items: OrderItem[]
}

export function previewOrder(data: OrderPreviewRequest): Promise<OrderPreview> {
  return request<OrderPreview>("/v1/orders/preview", {
    method: "POST",
    data
  })
}

export function createOrder(data: CreateOrderRequest): Promise<Order> {
  return request<Order>("/v1/orders", {
    method: "POST",
    data
  })
}

export function getMyOrders(status?: string, page: number = 1, size: number = 20): Promise<PageResult<Order>> {
  let url = "/v1/orders?page=" + page + "&size=" + size
  if (status) url += "&status=" + encodeURIComponent(status)
  return request<PageResult<Order>>(url)
}

export function getOrder(orderId: number): Promise<Order> {
  return request<Order>("/v1/orders/" + orderId)
}

export function cancelOrder(orderId: number): Promise<void> {
  return request<void>("/v1/orders/" + orderId + "/cancel", {
    method: "POST"
  })
}

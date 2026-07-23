import { request } from "../utils/request"

export interface CreateSosEventRequest {
  requestId: string
  latitude: number
  longitude: number
  locationAccuracyMeters?: number
  address: string
  message?: string
}

export interface SosEvent {
  id: string
  userId: string
  requestId: string
  latitude: number
  longitude: number
  locationAccuracyMeters: number | null
  address: string
  message: string | null
  status: string
  createdAt: string
}

export function createSosEvent(data: CreateSosEventRequest): Promise<SosEvent> {
  return request<SosEvent>("/v1/sos/events", { method: "POST", data })
}

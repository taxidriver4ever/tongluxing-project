import { request } from "../utils/request"

export interface Vehicle {
  vehicleId: number
  userId: number
  plateNoMask: string
  brand: string
  model: string
  vehicleType: string
  color: string
  seatCount: number
  energyType: string
  vehiclePhotoImageKey: string
  certificationStatus: string
  isDefault: boolean
}

export interface VehicleList {
  vehicles: Vehicle[]
}

export interface VehicleRequest {
  plateNo?: string
  brand: string
  model: string
  vehicleType: string
  color: string
  seatCount: number
  energyType: string
  vehiclePhotoImageKey: string
}

export interface VehicleCertification {
  vehicleId: number
  ownerName: string
  plateNoMask: string
  vinMask: string
  engineNoMask: string
  licenseImageKey: string
  status: string
  rejectReason: string
  submittedAt: string
  reviewedAt: string
}

export interface SubmitVehicleCertificationRequest {
  ownerName: string
  plateNo: string
  vin: string
  engineNo: string
  licenseImageKey: string
}

export interface PublicVehicleCard {
  vehicleId: number
  brand: string
  model: string
  vehicleType: string
  color: string
  plateNoMask: string
  certificationStatus: string
  isDefault: boolean
}

export interface VehicleAuthSubmitRequest {
  vehicleBrand: string
  vehicleModel: string
  plateNumber: string
  vehicleColor: string
  driverLicenseImages: string[]
  registrationLicenseImages: string[]
  vehicleImages: string[]
}

export interface VehicleAuthStatus {
  applyId?: number
  vehicleId?: number
  status: "UNSUBMITTED" | "PENDING" | "PASS" | "REJECT"
  reason?: string
  submitTime?: string
  auditTime?: string
}

export function getMyVehicles(): Promise<VehicleList> {
  return request<VehicleList>("/v1/vehicles/me")
}

export function createVehicle(data: VehicleRequest): Promise<Vehicle> {
  return request<Vehicle>("/v1/vehicles", {
    method: "POST",
    data
  })
}

export function getVehicle(vehicleId: number): Promise<Vehicle> {
  return request<Vehicle>("/v1/vehicles/" + vehicleId)
}

export function updateVehicle(vehicleId: number, data: VehicleRequest): Promise<Vehicle> {
  return request<Vehicle>("/v1/vehicles/" + vehicleId, {
    method: "PUT",
    data
  })
}

export function deleteVehicle(vehicleId: number): Promise<void> {
  return request<void>("/v1/vehicles/" + vehicleId, {
    method: "DELETE"
  })
}

export function setDefaultVehicle(vehicleId: number): Promise<Vehicle> {
  return request<Vehicle>("/v1/vehicles/" + vehicleId + "/default", {
    method: "PUT"
  })
}

export function submitVehicleCertification(vehicleId: number, data: SubmitVehicleCertificationRequest): Promise<VehicleCertification> {
  return request<VehicleCertification>("/v1/vehicles/" + vehicleId + "/certification", {
    method: "POST",
    data
  })
}

export function getVehicleCertification(vehicleId: number): Promise<VehicleCertification> {
  return request<VehicleCertification>("/v1/vehicles/" + vehicleId + "/certification")
}

export function getPublicVehicleCard(vehicleId: number): Promise<PublicVehicleCard> {
  return request<PublicVehicleCard>("/v1/vehicles/" + vehicleId + "/public-card")
}

/** 产品车辆认证闭环提交接口；当前图片按普通 URL/临时路径保存。 */
export function submitVehicleAuth(data: VehicleAuthSubmitRequest): Promise<VehicleAuthStatus> {
  return request<VehicleAuthStatus>("/v1/vehicle/auth/submit", { method: "POST", data })
}

/** 查询当前用户最近一次车辆认证申请状态。 */
export function getVehicleAuthStatus(): Promise<VehicleAuthStatus> {
  return request<VehicleAuthStatus>("/v1/vehicle/auth/status")
}

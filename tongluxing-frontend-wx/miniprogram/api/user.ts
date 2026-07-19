import { request } from "../utils/request"

export interface UserProfile {
  userId: number
  nickname: string
  avatarImageKey: string
  gender: number
  birthday: string
  cityCode: string
  cityName: string
  bio: string
  profileStatus: string
  certificationStatus: string
}

export interface UpdateUserProfileRequest {
  nickname?: string
  avatarImageKey?: string
  gender?: number
  birthday?: string
  cityCode?: string
  cityName?: string
  bio?: string
}

export interface CertificationRequest {
  realName: string
  idCardNo: string
  drivingLicenseImageKey: string
  faceImageKey: string
}

export interface Certification {
  certificationId: number
  userId: number
  certificationStatus: string
  rejectReason: string
  submittedAt: string
  reviewedAt: string
}

export interface PublicUserProfile {
  userId: number
  nickname: string
  avatarImageKey: string
  cityName: string
  bio: string
  certificationStatus: string
}

export function getCurrentUserProfile(): Promise<UserProfile> {
  return request<UserProfile>("/v1/users/me")
}

export function updateUserProfile(data: UpdateUserProfileRequest): Promise<UserProfile> {
  return request<UserProfile>("/v1/users/me/profile", {
    method: "PUT",
    data
  })
}

export function submitCertification(data: CertificationRequest): Promise<Certification> {
  return request<Certification>("/v1/users/me/certifications", {
    method: "POST",
    data
  })
}

export function getPublicUserProfile(userId: number): Promise<PublicUserProfile> {
  return request<PublicUserProfile>("/v1/users/" + userId + "/public-profile")
}

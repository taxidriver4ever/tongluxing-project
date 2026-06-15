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
  profileCompletion: number
  realNameStatus: string
}

export interface UpdateUserProfileRequest {
  nickname: string
  avatarImageKey: string
  gender: number
  birthday: string
  cityCode: string
  cityName: string
  bio: string
}

export interface UserPrivacy {
  userId: number
  profileVisible: boolean
  phoneVisible: boolean
  tripVisible: boolean
  locationVisible: boolean
  allowTeamInvite: boolean
  allowPrivateMessage: boolean
}

export interface UpdateUserPrivacyRequest {
  profileVisible: boolean
  phoneVisible: boolean
  tripVisible: boolean
  locationVisible: boolean
  allowTeamInvite: boolean
  allowPrivateMessage: boolean
}

export interface IdentityStatus {
  userId: number
  realName: string
  idCardNoMask: string
  faceImageKey: string
  status: string
  rejectReason: string
}

export interface SubmitIdentityRequest {
  realName: string
  idCardNo: string
  faceImageKey: string
}

export interface EmergencyContact {
  contactId: number
  contactName: string
  relation: string
  phoneMask: string
  isDefault: boolean
}

export interface EmergencyContactList {
  contacts: EmergencyContact[]
}

export interface EmergencyContactRequest {
  contactName: string
  relation: string
  phone: string
  isDefault: boolean
}

export interface PublicUserProfile {
  userId: number
  nickname: string
  avatarImageKey: string
  cityName: string
  realNameStatus: string
  vehicleCertified: boolean
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

export function getUserPrivacy(): Promise<UserPrivacy> {
  return request<UserPrivacy>("/v1/users/me/privacy")
}

export function updateUserPrivacy(data: UpdateUserPrivacyRequest): Promise<UserPrivacy> {
  return request<UserPrivacy>("/v1/users/me/privacy", {
    method: "PUT",
    data
  })
}

export function getIdentityStatus(): Promise<IdentityStatus> {
  return request<IdentityStatus>("/v1/users/me/identity-certification")
}

export function submitIdentity(data: SubmitIdentityRequest): Promise<IdentityStatus> {
  return request<IdentityStatus>("/v1/users/me/identity-certification", {
    method: "POST",
    data
  })
}

export function getEmergencyContacts(): Promise<EmergencyContactList> {
  return request<EmergencyContactList>("/v1/users/me/emergency-contacts")
}

export function addEmergencyContact(data: EmergencyContactRequest): Promise<EmergencyContact> {
  return request<EmergencyContact>("/v1/users/me/emergency-contacts", {
    method: "POST",
    data
  })
}

export function updateEmergencyContact(contactId: number, data: EmergencyContactRequest): Promise<EmergencyContact> {
  return request<EmergencyContact>("/v1/users/me/emergency-contacts/" + contactId, {
    method: "PUT",
    data
  })
}

export function deleteEmergencyContact(contactId: number): Promise<void> {
  return request<void>("/v1/users/me/emergency-contacts/" + contactId, {
    method: "DELETE"
  })
}

export function getPublicUserProfile(userId: number): Promise<PublicUserProfile> {
  return request<PublicUserProfile>("/v1/users/" + userId + "/public-profile")
}

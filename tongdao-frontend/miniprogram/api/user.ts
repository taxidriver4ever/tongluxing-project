import { request } from "../utils/request"

// ─── Basic Profile ────────────────────────────────────────────────────────────

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

// ─── Privacy ──────────────────────────────────────────────────────────────────

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

// ─── Identity Certification ───────────────────────────────────────────────────

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

// ─── Emergency Contact ────────────────────────────────────────────────────────

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

// ─── Public Profile ───────────────────────────────────────────────────────────

export interface PublicUserProfile {
  userId: number
  nickname: string
  avatarImageKey: string
  cityName: string
  realNameStatus: string
  vehicleCertified: boolean
}

// ─── Dashboard (v3 unified data) ─────────────────────────────────────────────

export interface GrowthSummary {
  totalPoints: number
  levelCode: string
  nextLevelPoints: number
}

export interface CouponCount {
  availableCount: number
  expiringCount: number
}

export interface InvitationSummary {
  validInviteCount: number
  nextRewardNeed: number
}

export interface TripDraftLocation {
  name: string
  address: string
  latitude: number
  longitude: number
}

export interface TripDraft {
  draftId: number
  startLocation: TripDraftLocation
  endLocation: TripDraftLocation
  departureTime: string
  durationDays: number
  peopleCount: number
  remark: string
  draftStatus: string
  publishedTripId: number | null
  updatedAt: string
}

export interface UserProfileVO {
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

export interface UserDashboard {
  profile: UserProfileVO
  growth: GrowthSummary
  coupon: CouponCount
  invitation: InvitationSummary
  nextTripDraft: TripDraft | null
}

// ─── Growth / Badges ─────────────────────────────────────────────────────────

export interface GrowthLog {
  id: number
  bizType: string
  bizId: string
  pointDelta: number
  balanceAfter: number
  remark: string
  createdAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface Badge {
  badgeId: number
  badgeCode: string
  badgeName: string
  badgeImageKey: string
  awardedAt: string
}

export interface BadgeWall {
  earned: Badge[]
  locked: Badge[]
}

// ─── Invite ───────────────────────────────────────────────────────────────────

export interface InviteCode {
  inviteCode: string
  scene: string
  enabled: boolean
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

// ─── Coupons ──────────────────────────────────────────────────────────────────

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

// ─── API Functions ────────────────────────────────────────────────────────────

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

// Dashboard (aggregated)
export function getUserDashboard(): Promise<UserDashboard> {
  return request<UserDashboard>("/v1/users/me/dashboard")
}

// Growth
export function getGrowthSummary(): Promise<GrowthSummary> {
  return request<GrowthSummary>("/v1/users/me/growth")
}

export function getGrowthLogs(page: number = 1, size: number = 20): Promise<PageResult<GrowthLog>> {
  return request<PageResult<GrowthLog>>("/v1/users/me/growth/logs?page=" + page + "&size=" + size)
}

export function getBadgeWall(): Promise<BadgeWall> {
  return request<BadgeWall>("/v1/users/me/badges")
}

// Invite
export function getMyInviteCode(): Promise<InviteCode> {
  return request<InviteCode>("/v1/users/me/invite-code")
}

export function getInvitationList(status?: string, page: number = 1, size: number = 20): Promise<PageResult<InvitationRecord>> {
  let url = "/v1/users/me/invitations?page=" + page + "&size=" + size
  if (status) url += "&status=" + status
  return request<PageResult<InvitationRecord>>(url)
}

export function getInviteRewardProgress(): Promise<InviteRewardProgress> {
  return request<InviteRewardProgress>("/v1/users/me/invitation-rewards")
}

// Coupons
export function getUserCoupons(status?: string, type?: string, page: number = 1, size: number = 20): Promise<PageResult<CouponSummary>> {
  let url = "/v1/users/me/coupons?page=" + page + "&size=" + size
  if (status) url += "&status=" + status
  if (type) url += "&type=" + type
  return request<PageResult<CouponSummary>>(url)
}

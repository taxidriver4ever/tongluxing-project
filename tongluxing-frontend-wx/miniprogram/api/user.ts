import { request } from "../utils/request"
export interface UserProfile { userId: number; nickname: string; avatarImageKey: string; gender: number; birthday: string; cityCode: string; cityName: string; bio: string; profileStatus: string; drivingLicenseCertificationStatus: string }
export interface UpdateUserProfileRequest { nickname?: string; avatarImageKey?: string; gender?: number; birthday?: string; cityCode?: string; cityName?: string; bio?: string }
export interface CertificationRequest { holderName: string; licenseNo: string; vehicleClass: string; firstIssueDate?: string; validFrom?: string; validTo?: string; issuingAuthority?: string; licenseFrontImageKey: string; licenseBackImageKey?: string; recognitionSource: "MINIPROGRAM_OCR" | "MANUAL_UPLOAD" }
export interface Certification { certificationId: number; userId: number; status: string; rejectReason: string; submittedAt: string; reviewedAt: string; canResubmit: boolean }
export interface PublicUserProfile { userId: number; nickname: string; avatarImageKey: string; cityName: string; bio: string; drivingLicenseCertificationStatus: string; totalTripCount: number; totalDistanceMeters: number; totalDurationMinutes: number; completedWaypointCount: number }
export interface PublicGrowthSummary { totalPoints: number; levelCode: string; nextLevelPoints: number }
export interface PublicBadge { badgeId: number; badgeCode: string; badgeName: string; badgeImageKey: string; conditionDescription: string; eventType: string; currentValue: number; threshold: number; awardedAt: string }
export interface PublicBadgeWall { earned: PublicBadge[]; locked: PublicBadge[] }
export interface UserHomepage { profile: PublicUserProfile; growth: PublicGrowthSummary; badges: PublicBadgeWall; ipProvince: string }
export function getCurrentUserProfile(): Promise<UserProfile> { return request<UserProfile>("/v1/users/me") }
export function updateUserProfile(data: UpdateUserProfileRequest): Promise<UserProfile> { return request<UserProfile>("/v1/users/me/profile", { method: "PUT", data }) }
export function submitCertification(data: CertificationRequest): Promise<Certification> { return request<Certification>("/v1/users/me/certifications", { method: "POST", data }) }
export function getLatestCertification(): Promise<Certification> { return request<Certification>("/v1/users/me/certifications/latest") }
export function getPublicUserProfile(userId: number): Promise<PublicUserProfile> { return request<PublicUserProfile>("/v1/users/" + userId + "/public-profile") }

export function getUserHomepage(userId: number): Promise<UserHomepage> { return request<UserHomepage>("/v1/users/" + userId + "/homepage") }

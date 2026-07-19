import { request } from "../utils/request"

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface GrowthSummary {
  totalPoints: number
  levelCode: string
  nextLevelPoints: number
}

export interface GrowthAccount {
  userId: number
  experience: number
  levelCode: string
}

export function getGrowthAccount(): Promise<GrowthAccount> {
  return request<GrowthAccount>("/v1/growth/account")
}

export interface GrowthLog {
  id: number
  bizType: string
  bizId: string
  pointDelta: number
  balanceAfter: number
  remark: string
  createdAt: string
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

export function getGrowthSummary(): Promise<GrowthSummary> {
  return request<GrowthSummary>("/v1/growth/me")
}

export function getGrowthLogs(page: number = 1, size: number = 20): Promise<PageResult<GrowthLog>> {
  return request<PageResult<GrowthLog>>("/v1/growth/me/logs?page=" + page + "&size=" + size)
}

export function getBadgeWall(): Promise<BadgeWall> {
  return request<BadgeWall>("/v1/growth/me/badges")
}

export function getPublicGrowthSummary(userId: number): Promise<GrowthSummary> {
  return request<GrowthSummary>("/v1/growth/users/" + userId + "/public-summary")
}

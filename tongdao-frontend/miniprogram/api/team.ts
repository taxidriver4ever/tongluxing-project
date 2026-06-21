import { request } from "../utils/request"

export interface Team {
  teamId: string
  tripId: string
  ownerUserId: string
  ownerVehicleId: string
  teamName: string
  teamDesc: string
  startName: string
  endName: string
  departureTime: string
  maxMemberCount: number
  currentMemberCount: number
  joinMode: string
  teamStatus: string
  publicFlag: boolean
  chatConversationId: string
  notice: string
  createdAt: string
}

export interface TeamMember {
  memberId: string
  teamId: string
  userId: string
  vehicleId: string
  memberRole: string
  memberStatus: string
  nicknameSnapshot: string
  vehicleSnapshot: string
  joinedAt: string
}

export interface TeamMemberList {
  members: TeamMember[]
}

export interface CreateTeamRequest {
  tripId: number
  ownerVehicleId: number
  teamName: string
  teamDesc: string
  maxMemberCount: number
  joinMode: string
  publicFlag: boolean
  notice: string
}

export interface JoinTeamApplicationRequest {
  applicantVehicleId?: number
  applyMessage: string
  joinQuestionJson: string
}

export interface TeamApplication {
  applicationId: string
  teamId: string
  tripId: string
  applicantUserId: string
  applicantVehicleId: string
  applicationStatus: string
  applyMessage: string
  reviewMessage: string
  reviewedAt: string
  createdAt: string
}

export function createTeam(data: CreateTeamRequest): Promise<Team> {
  return request<Team>("/v1/teams", {
    method: "POST",
    data
  })
}

export function getTeam(teamId: string): Promise<Team> {
  return request<Team>("/v1/teams/" + teamId)
}

export function getTeamMembers(teamId: string): Promise<TeamMemberList> {
  return request<TeamMemberList>("/v1/teams/" + teamId + "/members")
}

export function applyTeam(teamId: string, data: JoinTeamApplicationRequest): Promise<TeamApplication> {
  return request<TeamApplication>("/v1/teams/" + teamId + "/applications", {
    method: "POST",
    data
  })
}

export function reviewTeamApplication(applicationId: string, reviewAction: string, reviewMessage: string): Promise<TeamApplication> {
  return request<TeamApplication>("/v1/teams/applications/" + applicationId + "/review", {
    method: "POST",
    data: {
      reviewAction,
      reviewMessage
    }
  })
}

export function exitTeam(teamId: string): Promise<Team> {
  return request<Team>("/v1/teams/" + teamId + "/exit", {
    method: "POST"
  })
}

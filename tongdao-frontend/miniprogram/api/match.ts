import { request } from "../utils/request"

export interface MatchTripCard {
  tripId: string
  userId: string
  startName: string
  endName: string
  departureTime: string
  travelDepth: string
  matchScore: number
  overlapRate: number
  departureGapMinutes: number
  distanceGapMeters: number
}

export interface MatchTeamCard {
  teamId: string
  tripId: string
  teamName: string
  startName: string
  endName: string
  departureTime: string
  currentMemberCount: number
  maxMemberCount: number
  matchScore: number
  overlapRate: number
}

export interface MatchRecommendationList {
  tripId: string
  trips: MatchTripCard[]
  teams: MatchTeamCard[]
}

export interface NearbyTripList {
  trips: MatchTripCard[]
}

export interface NearbyTeamList {
  teams: MatchTeamCard[]
}

export function getTripRecommendations(tripId: string, limit: number): Promise<MatchRecommendationList> {
  return request<MatchRecommendationList>("/v1/matches/trips/" + tripId + "/recommendations?limit=" + limit)
}

export function getNearbyTrips(latitude: number, longitude: number, radiusMeters: number, limit: number): Promise<NearbyTripList> {
  return request<NearbyTripList>("/v1/matches/nearby-trips?latitude=" + latitude + "&longitude=" + longitude + "&radiusMeters=" + radiusMeters + "&limit=" + limit)
}

export function getNearbyTeams(latitude: number, longitude: number, radiusMeters: number, limit: number): Promise<NearbyTeamList> {
  return request<NearbyTeamList>("/v1/matches/nearby-teams?latitude=" + latitude + "&longitude=" + longitude + "&radiusMeters=" + radiusMeters + "&limit=" + limit)
}

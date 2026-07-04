import { request } from "../utils/request"

export interface TripWaypoint {
  name: string
  address: string
  latitude: number
  longitude: number
  sortOrder: number
}

export interface TripLocation {
  name: string
  address: string
  latitude: number
  longitude: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface Trip {
  tripId: string
  userId: string
  vehicleId: string
  startName: string
  startLat?: number
  startLng?: number
  startLocation: TripLocation
  endName: string
  endLat?: number
  endLng?: number
  endLocation: TripLocation
  routeSummary: string
  routePolylineKey: string
  routeDistance: number
  routeDuration: number
  routePolyline: string
  departureTime: string
  estimatedDays: number
  totalDistanceMeters: number
  maxVehicleCount: number
  joinedVehicleCount: number
  travelDepth: string
  publicFlag: boolean
  status: string
  remark: string
  waypoints: TripWaypoint[]
  createdAt: string
  updatedAt: string
}

export interface TripList {
  trips: Trip[]
}

export interface TripRequest {
  vehicleId: number
  startLocation: TripLocation
  endLocation: TripLocation
  routeSummary: string
  routeDistance: number
  routeDuration: number
  routePolyline: string
  departureTime: string
  estimatedDays: number
  maxVehicleCount: number
  travelDepth: string
  publicFlag: boolean
  remark: string
  waypoints: TripWaypoint[]
}

export interface TripMember {
  userId: string
  vehicleId: string
  memberRole: string
  joinStatus: string
  nicknameSnapshot: string
  vehicleSnapshot: string
  joinedAt: string
}

export interface TripDraftRequest {
  startLocation: TripLocation
  endLocation: TripLocation
  waypoints?: TripLocation[]
  departureTime: string
  durationDays: number
  peopleCount: number
  remark?: string
}

export interface TripDraft {
  draftId: number
  startLocation: TripLocation
  endLocation: TripLocation
  waypoints: TripLocation[]
  departureTime: string
  durationDays: number
  peopleCount: number
  remark: string
  draftStatus: string
  publishedTripId: number | null
  updatedAt: string
}

export interface PublishDraftRequest {
  publishType: "TRIP" | "TEAM"
}

export interface PublishDraftResult {
  draftId: number
  publishType: string
  publishedId: number
}

export interface TeamMatch {
  teamId: number
  teamName: string
  routeOverlapRate: number
  timeDifferenceMinutes: number
}

export function createTrip(data: TripRequest): Promise<Trip> {
  return request<Trip>("/v1/trips", {
    method: "POST",
    data
  })
}

export function updateTrip(tripId: string, data: TripRequest): Promise<Trip> {
  return request<Trip>("/v1/trips/" + tripId, {
    method: "PUT",
    data
  })
}

export function getMyTrips(scope: "active" | "history"): Promise<TripList> {
  return request<TripList>("/v1/trips/me?scope=" + scope)
}

export function getTrip(tripId: string): Promise<Trip> {
  return request<Trip>("/v1/trips/" + tripId)
}

export function endTrip(tripId: string): Promise<Trip> {
  return request<Trip>("/v1/trips/" + tripId + "/end", {
    method: "POST"
  })
}

export function cancelTrip(tripId: string): Promise<Trip> {
  return request<Trip>("/v1/trips/" + tripId + "/cancel", {
    method: "POST"
  })
}

export function getPublicTrips(limit: number): Promise<TripList> {
  return request<TripList>("/v1/trips/public?limit=" + limit)
}

export function getTripMembers(tripId: string): Promise<TripMember[]> {
  return request<TripMember[]>("/v1/trips/" + tripId + "/members")
}

export function createTripDraft(data: TripDraftRequest): Promise<TripDraft> {
  return request<TripDraft>("/v1/trip-drafts", {
    method: "POST",
    data
  })
}

export function getTripDrafts(status?: string, page: number = 1, size: number = 20): Promise<PageResult<TripDraft>> {
  let url = "/v1/trip-drafts?page=" + page + "&size=" + size
  if (status) url += "&status=" + encodeURIComponent(status)
  return request<PageResult<TripDraft>>(url)
}

export function updateTripDraft(id: number, data: TripDraftRequest): Promise<TripDraft> {
  return request<TripDraft>("/v1/trip-drafts/" + id, {
    method: "PUT",
    data
  })
}

export function deleteTripDraft(id: number): Promise<void> {
  return request<void>("/v1/trip-drafts/" + id, {
    method: "DELETE"
  })
}

export function publishTripDraft(id: number, data: PublishDraftRequest): Promise<PublishDraftResult> {
  return request<PublishDraftResult>("/v1/trip-drafts/" + id + "/publish", {
    method: "POST",
    data
  })
}

export function getTripDraftTeamRecommendations(id: number, page: number = 1, size: number = 20): Promise<PageResult<TeamMatch>> {
  return request<PageResult<TeamMatch>>("/v1/trip-drafts/" + id + "/team-recommendations?page=" + page + "&size=" + size)
}

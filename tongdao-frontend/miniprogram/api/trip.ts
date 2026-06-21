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

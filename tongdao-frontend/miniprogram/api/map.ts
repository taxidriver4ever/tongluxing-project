import { request } from "../utils/request"
import { TripLocation } from "./trip"

export interface RoutePlanRequest {
  startLocation: TripLocation
  endLocation: TripLocation
  waypoints: TripLocation[]
}

export interface RoutePlanResponse {
  routePlanId: string
  routeDistance: number
  routeDuration: number
  routePolyline: string
  routePoints: TripLocation[]
  providerType: string
  planStatus: string
}

export interface MapMarker {
  markerId: string
  markerType: string
  title: string
  subtitle: string
  latitude: number
  longitude: number
}

export interface NearbyMapResponse {
  markers: MapMarker[]
}

export function planRoute(data: RoutePlanRequest): Promise<RoutePlanResponse> {
  return request<RoutePlanResponse>("/v1/map/routes/plan", {
    method: "POST",
    data
  })
}

export function resolveLocation(data: TripLocation): Promise<TripLocation> {
  return request<TripLocation>("/v1/map/locations/resolve", {
    method: "POST",
    data
  })
}

export function getNearbyMap(latitude: number, longitude: number, radiusMeters: number): Promise<NearbyMapResponse> {
  return request<NearbyMapResponse>("/v1/map/nearby?latitude=" + latitude + "&longitude=" + longitude + "&radiusMeters=" + radiusMeters)
}

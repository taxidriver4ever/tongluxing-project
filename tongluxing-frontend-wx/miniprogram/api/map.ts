import { request } from "../utils/request"
import { TripLocation } from "./trip"
export interface RoutePlanRequest { startLocation: TripLocation; endLocation: TripLocation; waypoints: TripLocation[] }
export interface RoutePlanResponse { routePlanId: string; routeDistance: number; routeDuration: number; routePolyline: string; routePoints: TripLocation[]; providerType: string; planStatus: string }
export interface MapMarker { markerId: string; markerType: string; title: string; subtitle: string; latitude: number; longitude: number }
export interface NearbyMapResponse { markers: MapMarker[] }
export interface LocationSearchItem { historyId?: string; name: string; address: string; latitude: number; longitude: number; distanceMeters: number }
export function planRoute(data: RoutePlanRequest): Promise<RoutePlanResponse> { return request<RoutePlanResponse>("/v1/map/routes/plan", { method: "POST", data }) }
export function resolveLocation(data: TripLocation): Promise<TripLocation> { return request<TripLocation>("/v1/map/locations/resolve", { method: "POST", data }) }
export function searchLocations(keyword: string, latitude?: number, longitude?: number, limit = 20): Promise<LocationSearchItem[]> {
  let url = "/v1/map/locations/search?keyword=" + encodeURIComponent(keyword) + "&limit=" + limit
  if (latitude !== undefined && longitude !== undefined) url += "&latitude=" + latitude + "&longitude=" + longitude
  return request<LocationSearchItem[]>(url)
}
export function getLocationHistory(latitude?: number, longitude?: number, limit = 10): Promise<LocationSearchItem[]> {
  let url = "/v1/map/locations/history?limit=" + limit
  if (latitude !== undefined && longitude !== undefined) url += "&latitude=" + latitude + "&longitude=" + longitude
  return request<LocationSearchItem[]>(url)
}
export function deleteLocationHistory(historyId: string): Promise<number> { return request<number>("/v1/map/locations/history/" + historyId, { method: "DELETE" }) }
export function clearLocationHistory(): Promise<number> { return request<number>("/v1/map/locations/history", { method: "DELETE" }) }
export function getNearbyMap(latitude: number, longitude: number, radiusMeters: number): Promise<NearbyMapResponse> { return request<NearbyMapResponse>("/v1/map/nearby?latitude=" + latitude + "&longitude=" + longitude + "&radiusMeters=" + radiusMeters) }

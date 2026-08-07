import { apiRequest } from './apiClient.js'

function queryString(params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, value)
  })
  const text = query.toString()
  return text ? `?${text}` : ''
}

export const listTripTrackReviews = (params = {}) =>
  apiRequest(`/v1/admin/trip-track-reviews${queryString(params)}`)

export const getTripTrackReview = tripId =>
  apiRequest(`/v1/admin/trip-track-reviews/${tripId}`)

export const reviewTripTrack = (tripId, payload) =>
  apiRequest(`/v1/admin/trip-track-reviews/${tripId}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })

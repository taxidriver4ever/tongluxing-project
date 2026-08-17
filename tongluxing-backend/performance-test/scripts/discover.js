import http from 'k6/http';
import { sleep } from 'k6';
import { BASE_URL, apiOk, authHeaders, login, optionsFor } from './common.js';

export const options = optionsFor('discover');
export function setup() { return { token: login() }; }

export default function (data) {
  const sort = (__ENV.DISCOVER_SORT || 'RECOMMENDED').toUpperCase();
  const referenceTripId = __ENV.REFERENCE_TRIP_ID || '930000000000000000';
  const routeArg = sort === 'ROUTE_MATCH' ? `&referenceTripId=${referenceTripId}` : '';
  const response = http.get(`${BASE_URL}/api/v1/trips/discover?sort=${sort}&latitude=23.134700&longitude=113.361200&page=1&size=30${routeArg}`, {
    headers: authHeaders(data.token),
    tags: { endpoint: 'discover', sort },
  });
  apiOk(response, 'discover');
  sleep(Number(__ENV.THINK_TIME || 0.2));
}


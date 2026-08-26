import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { BASE_URL, authHeaders } from './common.js';

const userCount = Number(__ENV.USER_COUNT || 50);

export const options = {
  scenarios: {
    ttl_users: { executor: 'shared-iterations', vus: 1, iterations: userCount, maxDuration: '2m' },
  },
  thresholds: {
    checks: ['rate>0.98'],
    http_req_failed: ['rate<0.02'],
  },
};

export default function () {
  const number = exec.scenario.iterationInTest + 1;
  const phone = `139100000${String(number).padStart(2, '0')}`;
  const loginResponse = http.post(`${BASE_URL}/api/v1/auth/password-login`, JSON.stringify({
    phone,
    password: __ENV.TEST_PASSWORD,
    deviceId: `k6-ttl-${phone}`,
    clientType: 'APP_DRIVER',
  }), { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'ttl-user-login' } });
  let token = null;
  try { token = loginResponse.json().data.token; } catch (_) { /* checked below */ }
  if (!check(loginResponse, { 'TTL user login succeeds': (r) => r.status === 200 && Boolean(token) })) return;

  const response = http.get(`${BASE_URL}/api/v1/trips/recommend?sort_by=match_rate&user_has_trip=true&page=1&page_size=10&latitude=23.134700&longitude=113.361200`, {
    headers: authHeaders(token),
    tags: { endpoint: 'ttl-user-recommend' },
  });
  check(response, {
    'TTL user recommend succeeds': (r) => {
      try { const body = r.json(); return r.status === 200 && (body.code === 200 || body.code === 0); }
      catch (_) { return false; }
    },
  });
}

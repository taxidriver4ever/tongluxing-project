import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE_URL = (__ENV.BASE_URL || 'http://43.138.233.211').replace(/\/$/, '');

export function optionsFor(name) {
  return {
    vus: Number(__ENV.VUS || 1),
    duration: __ENV.DURATION || '30s',
    tags: { suite: 'tongluxing-backend', scenario: name },
    thresholds: {
      http_req_failed: [{ threshold: 'rate<0.02', abortOnFail: true, delayAbortEval: '20s' }],
      http_req_duration: [{ threshold: 'p(95)<5000', abortOnFail: true, delayAbortEval: '20s' }],
      checks: ['rate>0.98'],
    },
  };
}

export function login() {
  const phone = __ENV.TEST_PHONE;
  const password = __ENV.TEST_PASSWORD;
  if (!phone || !password) fail('必须设置 TEST_PHONE 和 TEST_PASSWORD');
  const response = http.post(`${BASE_URL}/api/v1/auth/password-login`, JSON.stringify({
    phone,
    password,
    deviceId: `k6-${phone}`,
    clientType: 'APP_DRIVER',
  }), { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } });
  const ok = check(response, { 'login HTTP 200': (r) => r.status === 200 });
  if (!ok) fail(`登录失败: HTTP ${response.status} ${response.body}`);
  const body = response.json();
  const token = body && body.data && body.data.token;
  if (!token) fail('登录响应缺少 data.token');
  return token;
}

export function authHeaders(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

export function apiOk(response, label) {
  return check(response, {
    [`${label} HTTP 200`]: (r) => r.status === 200,
    [`${label} business success`]: (r) => {
      try {
        const b = r.json();
        return b && (b.code === 200 || b.code === 0);
      } catch (_) {
        return false;
      }
    },
  });
}


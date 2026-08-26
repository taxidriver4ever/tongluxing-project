import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const duration = new Trend('recommend_diagnostic_duration', true);
const blocked = new Trend('recommend_diagnostic_blocked', true);
const connecting = new Trend('recommend_diagnostic_connecting', true);
const tlsHandshaking = new Trend('recommend_diagnostic_tls_handshaking', true);
const waiting = new Trend('recommend_diagnostic_waiting', true);
const receiving = new Trend('recommend_diagnostic_receiving', true);
const failed = new Rate('recommend_diagnostic_failed');
const timeouts = new Counter('recommend_diagnostic_timeouts');

const baseUrl = (__ENV.BASE_URL || '').replace(/\/$/, '');
const phone = __ENV.TEST_PHONE || '';
const password = __ENV.TEST_PASSWORD || '';
const summaryPath = __ENV.K6_TIMING_SUMMARY || 'k6-timing-summary.json';

export const options = {
  scenarios: {
    recommend_c5: {
      executor: 'constant-vus',
      vus: 5,
      duration: '60s',
      gracefulStop: '65s',
    },
  },
  thresholds: {
    recommend_diagnostic_failed: ['rate<1'],
  },
  discardResponseBodies: false,
};

export function setup() {
  if (!baseUrl || !phone || !password) {
    throw new Error('BASE_URL, TEST_PHONE and TEST_PASSWORD are required');
  }
  const response = http.post(`${baseUrl}/api/v1/auth/password-login`, JSON.stringify({
    phone,
    password,
    deviceId: `k6-recommend-network-${phone}`,
    clientType: 'APP_DRIVER',
  }), {
    headers: { 'Content-Type': 'application/json' },
    timeout: '15s',
  });
  if (response.status !== 200) {
    throw new Error(`login failed: status=${response.status} body=${response.body}`);
  }
  const payload = response.json();
  const token = payload?.data?.accessToken || payload?.data?.token || payload?.accessToken || payload?.token;
  if (!token) throw new Error('login response does not contain an access token');
  return { token };
}

export default function (data) {
  const requestId = `k6-${Date.now()}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`;
  const requestStartedAt = Date.now();
  const response = http.get(`${baseUrl}/api/v1/trips/recommend?page=1&size=20`, {
    headers: {
      Authorization: `Bearer ${data.token}`,
      'X-Request-Id': requestId,
      Accept: 'application/json',
    },
    timeout: '60s',
    tags: { endpoint: 'recommend-network-diagnostic' },
  });

  const timings = response.timings || {};
  const wallDurationMs = Date.now() - requestStartedAt;
  const measuredDurationMs = timings.duration > 0 ? timings.duration : wallDurationMs;
  duration.add(measuredDurationMs);
  blocked.add(timings.blocked || 0);
  connecting.add(timings.connecting || 0);
  tlsHandshaking.add(timings.tls_handshaking || 0);
  waiting.add(timings.waiting || 0);
  receiving.add(timings.receiving || 0);

  const errorText = response.error || '';
  const timeout = response.status === 0 && /timeout|timed out|deadline/i.test(errorText);
  const requestFailed = response.status !== 200;
  failed.add(requestFailed);
  if (timeout) timeouts.add(1);

  if (timeout || measuredDurationMs >= 1000) {
    console.error(`${timeout ? 'RECOMMEND_TIMEOUT' : 'RECOMMEND_SLOW'} ${JSON.stringify({
      requestId,
      vu: exec.vu.idInTest,
      iteration: exec.scenario.iterationInTest,
      status: response.status,
      durationMs: measuredDurationMs,
      blockedMs: timings.blocked || 0,
      connectingMs: timings.connecting || 0,
      tlsHandshakingMs: timings.tls_handshaking || 0,
      waitingMs: timings.waiting || 0,
      receivingMs: timings.receiving || 0,
      error: errorText,
      errorCode: response.error_code || 0,
    })}`);
  }

  check(response, {
    'recommend returned 200': (value) => value.status === 200,
    'request id propagated': (value) => !value.headers['X-Request-Id']
      || value.headers['X-Request-Id'] === requestId,
  });
}

function metricValues(data, name) {
  return data.metrics[name]?.values || {};
}

export function handleSummary(data) {
  const result = {
    generatedAt: new Date().toISOString(),
    scenario: { vus: 5, duration: '60s', requestTimeout: '60s' },
    requests: metricValues(data, 'http_reqs'),
    failed: metricValues(data, 'recommend_diagnostic_failed'),
    timeouts: metricValues(data, 'recommend_diagnostic_timeouts'),
    clientTimingsMs: {
      total: metricValues(data, 'recommend_diagnostic_duration'),
      blocked: metricValues(data, 'recommend_diagnostic_blocked'),
      connecting: metricValues(data, 'recommend_diagnostic_connecting'),
      tlsHandshaking: metricValues(data, 'recommend_diagnostic_tls_handshaking'),
      waiting: metricValues(data, 'recommend_diagnostic_waiting'),
      receiving: metricValues(data, 'recommend_diagnostic_receiving'),
    },
    checks: metricValues(data, 'checks'),
  };
  return {
    stdout: 'C5/60s recommendation network diagnosis finished.\n',
    [summaryPath]: JSON.stringify(result, null, 2),
  };
}

import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, authHeaders, login } from './common.js';

const runMode = (__ENV.RUN_MODE || 'iterations').toLowerCase();
const vus = Number(__ENV.VUS || 1);
const duration = __ENV.DURATION || '30s';
const iterations = Number(__ENV.ITERATIONS || vus);

export const options = {
  scenarios: runMode === 'duration' ? {
    recommend_duration: { executor: 'constant-vus', vus, duration, gracefulStop: '10s' },
  } : {
    recommend_iterations: { executor: 'shared-iterations', vus, iterations, maxDuration: '2m' },
  },
  thresholds: {
    recommend_business_failed: [{ threshold: 'rate<0.02', abortOnFail: runMode === 'duration', delayAbortEval: '20s' }],
    recommend_http_5xx: [{ threshold: 'rate<0.02', abortOnFail: runMode === 'duration', delayAbortEval: '20s' }],
    recommend_duration_ms: [{ threshold: 'p(95)<5000', abortOnFail: runMode === 'duration', delayAbortEval: '20s' }],
  },
};

const recommendDuration = new Trend('recommend_duration_ms', true);
const businessFailed = new Rate('recommend_business_failed');
const http5xx = new Rate('recommend_http_5xx');
const resultCount = new Trend('recommend_result_count');
const requests = new Counter('recommend_requests');

export function setup() {
  return { token: login() };
}

function responseList(body) {
  if (!body || !body.data) return [];
  const data = body.data;
  return data.list || data.items || data.records || data.content || [];
}

export default function (data) {
  const baseLatitude = Number(__ENV.LATITUDE || '23.134700');
  const uniqueKeys = (__ENV.UNIQUE_KEYS || 'false').toLowerCase() === 'true';
  const latitude = (baseLatitude + (uniqueKeys ? exec.scenario.iterationInTest * 0.001 : 0)).toFixed(6);
  const longitude = __ENV.LONGITUDE || '113.361200';
  const pageSize = Number(__ENV.PAGE_SIZE || 30);
  const userHasTrip = (__ENV.USER_HAS_TRIP || 'true').toLowerCase();
  const url = `${BASE_URL}/api/v1/trips/recommend?sort_by=match_rate&user_has_trip=${userHasTrip}&page=1&page_size=${pageSize}&latitude=${latitude}&longitude=${longitude}`;
  const response = http.get(url, {
    headers: authHeaders(data.token),
    tags: { endpoint: 'recommend-only', case_name: __ENV.CASE_NAME || 'unspecified' },
  });
  let body = null;
  try { body = response.json(); } catch (_) { /* recorded as business failure below */ }
  const businessOk = response.status === 200 && body && (body.code === 200 || body.code === 0);
  const list = responseList(body);
  recommendDuration.add(response.timings.duration);
  businessFailed.add(!businessOk);
  http5xx.add(response.status >= 500);
  resultCount.add(list.length);
  requests.add(1);
  check(response, {
    'recommend HTTP 200': (r) => r.status === 200,
    'recommend business success': () => Boolean(businessOk),
  });
  if ((__ENV.LOG_EACH_RESULT || 'false').toLowerCase() === 'true') {
    const tripIds = list.map((item) => item.tripId || item.id).filter((value) => value !== undefined && value !== null);
    console.log(`RECOMMEND_RESULT ${JSON.stringify({
      iteration: exec.scenario.iterationInTest,
      durationMs: Math.round(response.timings.duration * 100) / 100,
      status: response.status,
      businessCode: body && body.code,
      count: list.length,
      tripIds,
    })}`);
  }
  if (runMode === 'duration') sleep(Number(__ENV.THINK_TIME || 0.2));
}

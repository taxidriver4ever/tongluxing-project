import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { BASE_URL, authHeaders, login, optionsFor } from './common.js';

export const options = (() => {
  const value = optionsFor('track-batch');
  if (__ENV.ITERATIONS) {
    delete value.duration;
    value.iterations = Number(__ENV.ITERATIONS);
  }
  return value;
})();
export function setup() { return { token: login() }; }

function point(tripId, sequenceNo, epochMs, index) {
  const offset = sequenceNo % 1000;
  const offsetHours = Number(__ENV.SERVER_UTC_OFFSET_HOURS || 8);
  // 服务端 LocalDateTime 无时区且当前部署为 Asia/Shanghai；转换后仍保留每个点的真实顺序。
  const time = new Date(epochMs + index * 3000 + offsetHours * 3600000).toISOString().replace('Z', '');
  return {
    // 18 位 Snowflake ID 超出 JavaScript 安全整数范围，必须作为 JSON 字符串发送。
    tripId: String(tripId), longitude: 113.3612 + offset * 0.0002,
    latitude: 23.1347 + offset * 0.0002, altitude: 20, speed: 100,
    direction: 45, accuracy: 8, recordTime: time, clientSendTime: time,
    deviceId: 'k6-track-device', sequenceNo, mockLocation: false,
    provider: 'PERFORMANCE_TEST', batteryLevel: 80, appState: 'FOREGROUND',
  };
}

export default function (data) {
  const tripId = __ENV.TRACK_TRIP_ID || '930000000000000000';
  const size = Math.min(200, Math.max(1, Number(__ENV.BATCH_SIZE || 50)));
  const mode = (__ENV.TRACK_MODE || 'batch').toLowerCase();
  const retry = (__ENV.RETRY_MODE || 'none').toLowerCase();
  const base = Number(__ENV.SEQUENCE_BASE || 1200000000) + (__VU * 10000000) + (exec.scenario.iterationInTest * 1000);
  const now = Date.now() - size * 3000;
  const points = Array.from({ length: size }, (_, i) => point(tripId, base + i, now, i));
  const params = { headers: authHeaders(data.token), tags: { endpoint: `track-${mode}`, batch_size: String(size), retry } };
  let responses;
  if (mode === 'single') {
    responses = [];
    points.forEach((p) => {
      responses.push(http.post(`${BASE_URL}/api/v1/driver-tracks/points`, JSON.stringify(p), params));
      sleep(Number(__ENV.SIMULATED_RTT_MS || 0) / 1000);
    });
  } else {
    const body = JSON.stringify({ points });
    responses = [http.post(`${BASE_URL}/api/v1/driver-tracks/points/batch`, body, params)];
    if (retry === 'duplicate') responses.push(http.post(`${BASE_URL}/api/v1/driver-tracks/points/batch`, body, params));
    if (retry === 'overlap') {
      const overlap = points.slice(Math.floor(size / 2)).concat(Array.from({ length: Math.floor(size / 2) }, (_, i) => point(tripId, base + size + i, Date.now(), i)));
      responses.push(http.post(`${BASE_URL}/api/v1/driver-tracks/points/batch`, JSON.stringify({ points: overlap }), params));
    }
  }
  if ((__ENV.DEBUG_RESPONSE || 'false') === 'true' && exec.scenario.iterationInTest === 0) {
    console.log(`track response: ${responses[0].body}`);
  }
  responses.forEach((response) => check(response, {
    'track HTTP 200': (r) => r.status === 200,
    'track business success': (r) => { try { return r.json().code === 200; } catch (_) { return false; } },
    'track batch has no unexpected rejects': (r) => {
      if (mode === 'single' || retry !== 'none') return true;
      try { return r.json().data.rejectedSequenceNos.length === 0 && r.json().data.acceptedSequenceNos.length === size; } catch (_) { return false; }
    },
  }));
  if (mode !== 'single') sleep(Number(__ENV.SIMULATED_RTT_MS || 0) / 1000);
}

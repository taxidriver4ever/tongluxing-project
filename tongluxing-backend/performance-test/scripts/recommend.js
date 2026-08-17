import http from 'k6/http';
import { sleep } from 'k6';
import exec from 'k6/execution';
import { BASE_URL, apiOk, authHeaders, login, optionsFor } from './common.js';

export const options = optionsFor('recommend');

export function setup() { return { token: login() }; }

export default function (data) {
  const mode = (__ENV.CACHE_MODE || 'hit').toLowerCase();
  const candidateCount = Number(__ENV.CANDIDATE_COUNT || 1000);
  // 服务端缓存 key 把坐标保留 3 位；每次跨越 0.001 才能构造真实 miss。
  // 限制在 100 个短期 key 内，避免给 Redis 制造无意义的长期压力。
  const nonce = mode === 'miss' ? (exec.scenario.iterationInTest % 100) : 0;
  const latitude = (23.1347 + nonce * 0.001).toFixed(6);
  const url = `${BASE_URL}/api/v1/trips/recommend?sort_by=match_rate&user_has_trip=true&page=1&page_size=30&latitude=${latitude}&longitude=113.361200`;
  const response = http.get(url, {
    headers: authHeaders(data.token),
    tags: { endpoint: 'recommend', cache_mode: mode, candidate_count: String(candidateCount) },
  });
  apiOk(response, 'recommend');
  sleep(Number(__ENV.THINK_TIME || 0.2));
}

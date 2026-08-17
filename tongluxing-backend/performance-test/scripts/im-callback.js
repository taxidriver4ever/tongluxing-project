import http from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { BASE_URL } from './common.js';

const signatures = JSON.parse(open(__ENV.IM_SIGNATURE_FILE || '../data/im-signatures.json'));
const mode = (__ENV.IM_LOAD_MODE || 'rate').toLowerCase();
const duration = __ENV.DURATION || '30s';

export const options = {
  scenarios: mode === 'burst' ? {
    im_burst: {
      executor: 'shared-iterations', vus: Number(__ENV.BURST_VUS || 50),
      iterations: Number(__ENV.BURST_SIZE || 50), maxDuration: '30s',
    },
  } : {
    im_rate: {
      executor: 'constant-arrival-rate', rate: Number(__ENV.RPS || 10), timeUnit: '1s',
      duration, preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 20),
      maxVUs: Number(__ENV.MAX_VUS || 100),
    },
  },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.02', abortOnFail: true, delayAbortEval: '10s' }],
    http_req_duration: [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '10s' }],
    checks: ['rate>0.98'],
  },
};

function signedSecond() {
  const now = Math.floor(Date.now() / 1000);
  // 测试机与服务器可能有少量时钟偏差；服务端验签窗口为 ±60s，这里收紧到 ±30s。
  for (let delta = 0; delta <= 30; delta += 1) {
    const future = String(now + delta);
    if (signatures[future]) return { requestTime: future, sign: signatures[future] };
    const past = String(now - delta);
    if (signatures[past]) return { requestTime: past, sign: signatures[past] };
  }
  fail(`签名映射不包含当前时间 ${now}，请重新生成短期签名文件`);
}

export default function () {
  const signed = signedSecond();
  const sdkAppId = __ENV.IM_SDK_APP_ID;
  if (!sdkAppId) fail('必须设置 IM_SDK_APP_ID');
  const identity = (__ENV.IDEMPOTENCY_KEY || 'MsgKey').toLowerCase();
  const duplicate = (__ENV.DUPLICATE || 'true').toLowerCase() === 'true';
  const unique = duplicate ? 'perf-fixed' : `perf-${exec.scenario.iterationInTest}`;
  const body = {
    GroupId: __ENV.IM_GROUP_ID || 'trip_212254470857936896',
    From_Account: __ENV.IM_FROM_ACCOUNT || 'u_910000000000000101',
    MsgSeq: 700001, MsgRandom: '800001', MsgTime: Number(signed.requestTime),
    MsgBody: [{ MsgType: 'TIMTextElem', MsgContent: { Text: 'PERFORMANCE_TEST idempotency' } }],
  };
  if (identity === 'msgkey') body.MsgKey = unique;
  else if (identity === 'msgid') body.MsgId = unique;
  else { body.MsgSeq = duplicate ? 700001 : 700001 + exec.scenario.iterationInTest; body.MsgRandom = duplicate ? '800001' : String(800001 + exec.scenario.iterationInTest); }
  const url = `${BASE_URL}/api/v1/callbacks/tencent-im?SdkAppid=${sdkAppId}&CallbackCommand=Group.CallbackAfterSendMsg&RequestTime=${signed.requestTime}&Sign=${signed.sign}`;
  const response = http.post(url, JSON.stringify(body), { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'im-callback', identity } });
  if ((__ENV.DEBUG_RESPONSE || 'false') === 'true' && exec.scenario.iterationInTest === 0) console.log(`IM response: ${response.body}`);
  check(response, {
    'IM ACK HTTP 200': (r) => r.status === 200,
    'IM ACK ErrorCode 0': (r) => { try { return r.json().ErrorCode === 0; } catch (_) { return false; } },
  });
}

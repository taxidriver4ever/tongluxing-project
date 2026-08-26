import fs from "node:fs/promises";
import path from "node:path";
import { Workbook } from "@oai/artifact-tool";

const root = String.raw`D:\tongluxing-intern\自驾社交平台开发文档\21_测试文档\压测结果`;
const dataDir = path.join(root, "推荐接口整改后压测原始数据", "20260817-fix-retest");
const round = (value, digits = 2) => Number(Number(value).toFixed(digits));
function percentile(values, q) {
  const sorted = [...values].sort((a, b) => a - b);
  const pos = (sorted.length - 1) * q;
  const lo = Math.floor(pos), hi = Math.ceil(pos);
  return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
}
const csvCell = (value) => {
  const text = value == null ? "" : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
};
const toCsv = (rows) => rows.map((row) => row.map(csvCell).join(",")).join("\r\n") + "\r\n";
const loadSummary = async (name) => JSON.parse(await fs.readFile(path.join(dataDir, name), "utf8"));
const loadRawValues = async (name) => {
  const values = [];
  for (const line of (await fs.readFile(path.join(dataDir, name), "utf8")).split(/\r?\n/)) {
    if (line.includes('"metric":"recommend_duration_ms"')) values.push(Number(JSON.parse(line).data.value));
  }
  return values;
};

const c1 = await loadSummary("overall-pool-c1-summary.json");
const c1Raw = await loadRawValues("overall-pool-c1-raw.json");
const c5Warm = await loadSummary("overall-pool-c5-valid-warmup-summary.json");
const c5WarmRaw = await loadRawValues("overall-pool-c5-valid-warmup-raw.json");
const c5Diag = await loadSummary("overall-pool-c5-diagnostic-60s-summary.json");
const c5DiagRaw = await loadRawValues("overall-pool-c5-diagnostic-60s-raw.json");

function overallRow(label, concurrency, duration, s, values, tailCount, validity) {
  const m = s.metrics;
  return [label, concurrency, duration, m.recommend_requests.count, round(m.recommend_requests.rate, 3),
    round(percentile(values, .50)), round(percentile(values, .95)), round(percentile(values, .99)),
    round(Math.max(...values)), round(m.recommend_business_failed.value * 100, 3),
    round(m.recommend_http_5xx.value * 100, 3), round(m.recommend_result_count.avg, 3), tailCount, validity];
}

const overallRows = [
  ["阶段", "并发", "持续时间", "推荐请求数", "QPS", "P50_ms", "P95_ms", "P99_ms", "Max_ms", "业务错误率_pct", "HTTP_5xx_pct", "平均结果数", "超10秒或中断数", "有效性"],
  overallRow("推荐池存在-正式", 1, "3m", c1, c1Raw, 0, "有效（2026-08-17）"),
  overallRow("推荐池存在-有效预热", 5, "30s", c5Warm, c5WarmRaw, 2, "有效，但2个VU超过10秒后被中断"),
  overallRow("推荐池存在-停止诊断", 5, "60s", c5Diag, c5DiagRaw, 2, "有效；2次响应体接收60秒超时，触发停止"),
];

const evidenceRows = [
  ["分类", "场景", "样本/并发", "指标", "结果", "结论"],
  ["Cache Miss", "50候选 C1", "1", "完整冷构建", "80.43ms", "PASS"],
  ["Cache Miss", "200候选 C1", "1", "完整冷构建", "125.50ms", "PASS"],
  ["Cache Miss", "500候选 C1", "1", "完整冷构建", "126.84ms", "PASS"],
  ["Cache Miss", "1000候选 C1（8月17日）", "1", "完整冷构建", "252.10ms", "PASS"],
  ["Cache Miss", "1000候选数据刷新后首读（8月22日）", "1", "total/routeDatabase", "4344ms / 4286ms", "RISK"],
  ["推荐池", "连续刷新", "10次", "平均/P95/Max", "63.14/128.55/183.18ms，批次不重叠", "PASS"],
  ["Single-flight", "同Key冷Miss", "C2", "完整计算次数/P95/Max", "1 / 187.67 / 188.25ms", "PASS"],
  ["Single-flight", "同Key冷Miss", "C5", "完整计算次数/P95/Max", "1 / 237.41 / 237.82ms", "PASS"],
  ["Single-flight", "同Key冷Miss", "C10", "完整计算次数/P95/Max", "1 / 249.50 / 255.94ms", "PASS"],
  ["Follower", "stale兜底", "1", "耗时/5xx", "570.15ms / 0", "PASS"],
  ["Follower", "无stale锁超时", "1", "耗时/5xx", "555.98ms / 0", "PASS"],
  ["TTL", "50个用户Key", "50", "剩余TTL min/avg/max", "154/183.52/218s", "PASS"],
  ["Relationship", "pageSize 10/20/30", "3次", "关系SQL", "每请求1次成员+1次申请", "PASS"],
  ["IMPRESSION", "10/20/30条", "3次", "INSERT次数/批量行数", "每请求1次 / 10、20、30行", "PASS"],
  ["IMPRESSION", "表锁20秒", "1", "HTTP耗时", "78.65ms正常返回", "PASS"],
  ["候选轻量化", "1000候选", "静态+日志", "第一阶段/详情", "1000轻量Candidate + 最终10~30详情；detailDatabaseMs约2", "PASS"],
  ["指标SQL", "EXPLAIN ANALYZE", "1000", "聚合耗时/循环", "约2.97ms；application/favorite索引lookup各1000 loops", "RISK"],
  ["match_polyline", "GET热路径检查", "静态", "潜在UPDATE", "仍可能归一化并回填", "RISK"],
  ["端到端长尾", "C5/60秒", "521", "P95/Max/超时", "380.55ms / 60000.67ms / 2次", "FAIL"],
  ["资源", "C5诊断", "2 vCPU", "Backend CPU/内存", "约12.48%~17.10% / 424.7~425.0MiB", "PASS"],
  ["数据库", "C5诊断", "MySQL", "CPU/Threads_running/Max_used", "约8.37%~9.94% / 2~3 / 12", "PASS"],
];

const outputs = [["推荐接口整体性能汇总.csv", overallRows, "Overall"], ["推荐接口专项验证汇总.csv", evidenceRows, "Evidence"]];
for (const [name, rows, sheetName] of outputs) {
  const csv = toCsv(rows);
  const workbook = await Workbook.fromCSV(csv, { sheetName });
  const checked = await workbook.inspect({kind: "table", range: `${sheetName}!A1:N${rows.length}`,
    include: "values", tableMaxRows: rows.length, tableMaxCols: 14, maxChars: 9000});
  if (!checked.ndjson.includes(rows[0][0])) throw new Error(`CSV verification failed: ${name}`);
  const preview = await workbook.render({sheetName, autoCrop: "all", scale: 1, format: "png"});
  await fs.writeFile(path.join(dataDir, `${name}.preview.png`), new Uint8Array(await preview.arrayBuffer()));
  await fs.writeFile(path.join(dataDir, name), "\uFEFF" + csv, "utf8");
}

let sanitized = 0;
for (const name of (await fs.readdir(dataDir)).filter((n) => n.endsWith("-summary.json"))) {
  const file = path.join(dataDir, name);
  const parsed = JSON.parse(await fs.readFile(file, "utf8"));
  if (parsed.setup_data && Object.hasOwn(parsed.setup_data, "token")) {
    parsed.setup_data.token = "[REDACTED]";
    await fs.writeFile(file, JSON.stringify(parsed, null, 2) + "\n", "utf8");
    sanitized += 1;
  }
}

console.log(JSON.stringify({c1P99: round(percentile(c1Raw, .99)), c5WarmP99: round(percentile(c5WarmRaw, .99)),
  c5DiagnosticP99: round(percentile(c5DiagRaw, .99)), sanitizedSummaryFiles: sanitized,
  outputs: outputs.map(([name]) => path.join(dataDir, name))}, null, 2));

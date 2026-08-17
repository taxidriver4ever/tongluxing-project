# 后端专项压测

目标环境通过 `BASE_URL` 指定；账号、密码、IM 签名等敏感参数只从环境变量或临时文件读取。

## 快速开始

```powershell
$env:BASE_URL = 'http://43.138.233.211'
$env:TEST_PHONE = '13910000001'
$env:TEST_PASSWORD = '<测试密码>'
$env:VUS = '1'
$env:DURATION = '30s'
.\tools\k6-v2.1.0-windows-amd64\k6.exe run .\scripts\recommend.js
```

正式执行顺序固定为 C1、C5、C10、C20、C30、C50。每档先预热再正式运行；若 5xx 超过 2%、P95 超过 5 秒、服务器无响应或出现资源池/OOM 风险，立即停止升档。

- `data/performance_candidates.sql`：创建独立 ID 段的 1000 条候选、路线和车队；可重复执行。
- `scripts/recommend.js`：推荐缓存命中/未命中与候选规模测试。
- `scripts/discover.js`：发现页普通排序与 `ROUTE_MATCH`。
- `scripts/track-batch.js`：单点/批量、批大小、模拟 RTT、重复与重叠重试。
- `scripts/im-callback.js`：腾讯 IM 快 ACK 和幂等性；需要短期签名映射文件。
- `scripts/run-stage.ps1`：单档执行并保存 k6 summary。
- `scripts/monitor-server.ps1`：只读采集 Docker、日志、MySQL、Redis 指标。

所有写入型脚本均使用 `PERF` 标识和 `930000000000000000` 起始的独立 ID 范围。清理不是默认动作，避免误删；如需清理必须先人工核对范围。


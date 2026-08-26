# 推荐接口端到端长尾诊断报告

## 1. 测试范围

- 接口：`GET /api/v1/trips/recommend`
- 服务器：`http://43.138.233.211`
- 场景：固定 C5，持续 60 秒
- 执行时间：2026-08-22 15:28:51 ～ 15:30:05（Asia/Shanghai）
- 本轮仅增加日志、requestId、响应字节统计和诊断脚本；未修改推荐算法、缓存策略、SQL 或排序逻辑。

## 2. 总体结果

| 指标 | 结果 |
|---|---:|
| 推荐请求尝试数 | 255 |
| 成功返回数 | 250 |
| timeout 数 | 5 |
| 成功率 | 98.04% |
| 客户端总耗时平均值 | 594.81 ms |
| 客户端总耗时 P95 | 1490.41 ms |
| 客户端总耗时最大值 | 3831.21 ms |
| 客户端 waiting P95 | 1489.62 ms |
| 客户端 receiving P95 | 2.30 ms |
| Nginx request_time P95 / 最大值 | 322 ms / 1305 ms |
| Nginx upstream_response_time P95 / 最大值 | 322 ms / 1305 ms |
| Backend backendTotalMs P95 / 最大值 | 320 ms / 1302 ms |
| Backend businessTotalMs P95 / 最大值 | 299 ms / 1218 ms |
| JSON 序列化 P95 / 最大值 | 8 ms / 27 ms |
| 最大响应体 | 5354 bytes |

说明：k6 的 `http_reqs=256` 包含 1 次 setup 登录；上表只统计 255 次推荐请求。timeout 的 k6 timing 值为 0，因此客户端汇总中的最小值和平均值会受到这 5 个零值影响。

## 3. timeout requestId 对齐

5 个 timeout 均为 k6 `dial: i/o timeout`（错误码 1211），并且在 Nginx 与 Backend 中都找不到对应 requestId。

| requestId | k6 | Nginx | Backend |
|---|---|---|---|
| `k6-1787383750993-2-143` | dial timeout | 无请求记录 | 无请求记录 |
| `k6-1787383752069-1-147` | dial timeout | 无请求记录 | 无请求记录 |
| `k6-1787383752078-3-148` | dial timeout | 无请求记录 | 无请求记录 |
| `k6-1787383752640-5-150` | dial timeout | 无请求记录 | 无请求记录 |
| `k6-1787383756470-4-154` | dial timeout | 无请求记录 | 无请求记录 |

结论：这 5 个 timeout 没有到达 Nginx，更没有进入 Spring Boot。它们不是推荐业务计算、JSON 序列化、Nginx 等待 Backend 或大响应体发送导致的 60 秒阻塞，而是发生在客户端到服务器的 TCP 建连阶段或建连之前的客户端网络栈中。

## 4. 最慢成功请求完整链路

requestId：`k6-1787383741392-5-75`

| 环节 | 耗时/结果 |
|---|---:|
| k6 blocked | 0.74 ms |
| k6 connecting | 0.67 ms |
| k6 waiting | 3830.00 ms |
| k6 receiving | 1.17 ms |
| k6 total | 3831.21 ms |
| Nginx request_time | 31 ms |
| Nginx upstream_response_time | 31 ms |
| Nginx upstream_header_time | 31 ms |
| Backend businessTotalMs | 17 ms |
| Backend backendTotalMs | 30 ms |
| Backend jsonSerializeMs | 2 ms |
| 响应体 | 5352 bytes |

该请求在 Backend 与 Nginx 中都只消耗约 30 ms，但客户端等待约 3.83 秒；Nginx 自身发送尾差只有约 0 ms，响应体也只有约 5.2 KB。长尾主要位于 k6 客户端与 Nginx 之间的公网/本机 Docker 网络路径，不在 Spring 推荐计算、JSON 序列化或 Nginx 到 Backend 的上游链路。

## 5. Backend 与 Nginx 观测

- Nginx 共记录 250 条推荐请求，Backend 生命周期日志也为 250 条，与 250 个成功响应完全一致。
- 收到的请求中，没有 `backendTotalMs > 5s` 的样本，也没有 `upstream_response_time > 5s` 的样本。
- Nginx `request_time - upstream_response_time` 最大约 1 ms，没有发现 Nginx 在拿到上游响应后长时间向客户端发送响应体。
- 冷推荐池样本 `k6-1787383734422-2-4`：候选 980，业务计算 1218 ms，Backend 总计 1302 ms，Nginx 总计 1305 ms。三层数据一致，证明 requestId 链路与计时口径有效。
- JSON 序列化最大 27 ms，不是当前长尾主因。
- 4 个响应约 163 bytes，246 个响应约 5.2 KB；大响应仍很小，没有观察到响应大小与 timeout 的关联。

## 6. TCP 与连接复用

服务器宿主机 `netstat -s` 在压测前后均为：

- `segments retransmitted = 89328`
- `TCPLostRetransmit = 32854`
- `fast retransmits = 5008`
- `retransmits in slow start = 1724`
- `TCPSynRetrans = 12100`

本轮这些计数没有增长，因此没有服务器宿主机侧重传增加的证据。但这不能排除客户端、本机 Docker NAT、公网中间链路或容器网络命名空间内的问题。

另一个重要现象：250 个成功请求对应 250 个不同的 Nginx `$connection`，且 `$connection_requests` 全部为 1。Nginx 健康检查响应明确返回 `Connection: keep-alive`，但本轮 k6 客户端没有复用 TCP 连接。每请求新建连接会放大 Docker NAT、公网链路、临时端口或连接跟踪异常的概率，与本轮 `dial: i/o timeout` 现象方向一致；目前只能标记为高优先级风险，尚不能仅凭本轮数据断言唯一根因。

## 7. 最终判断

### A. Backend 慢

不成立。Backend 最大 1302 ms，P95 320 ms；没有超过 5 秒的样本。

### B. Nginx 等待 Backend

不成立。Nginx upstream 最大 1305 ms，且与 Backend 总耗时高度一致；没有超过 5 秒的样本。

### C. 网络/连接建立异常

成立，证据充分：

1. 5 个 timeout 全部是 `dial: i/o timeout`。
2. 5 个 requestId 在 Nginx 和 Backend 中均为 0 条记录。
3. 最慢成功请求客户端 3831 ms，而 Nginx/Backend 仅约 31/30 ms。
4. 成功请求也完全没有 TCP 连接复用。
5. `receiving` P95 仅 2.30 ms，因此本轮不是“大响应体接收 60 秒”，而是连接建立或客户端到 Nginx 的网络等待异常。

综合判断：此前十几秒/60 秒长尾不能由 Spring 内部几百毫秒的 `trip_recommend_timing` 解释；本轮已明确把主要风险定位到 k6 所在的本机 Docker 网络/NAT、客户端连接复用、客户端到服务器公网路径或中间连接跟踪层。Spring、推荐算法、JSON 序列化以及 Nginx 上游转发不是本轮 timeout 的发生位置。

## 8. 后续建议

1. 下一轮保持相同 C5，不升压，优先对比“本机原生 k6”与“Docker 内 k6”，确认 Docker Desktop NAT 是否参与故障。
2. 检查 k6/运行环境是否设置 `noConnectionReuse`、代理环境变量或其他导致每请求新建连接的配置。
3. 同时抓取客户端与服务器 `tcpdump`，并采集 Nginx 容器网络命名空间的 `/proc/net/snmp`、宿主机 conntrack 使用率。
4. 保留本次 requestId、Nginx `recommend_perf` 和 Backend 生命周期日志，后续复现时继续按 requestId 对齐。

## 9. 原始证据文件

- `performance-test/results/recommend-network-diagnosis/k6-timing-summary.json`
- `performance-test/results/recommend-network-diagnosis/nginx-recommend-access.log`
- `performance-test/results/recommend-network-diagnosis/backend-recommend-timing.log`

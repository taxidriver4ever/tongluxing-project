# 同路行后端简历优化项专项压测报告

> 测试日期：2026-08-17（Asia/Shanghai）  
> 目标：`http://43.138.233.211`  
> 工具：k6 v2.1.0、Maven/JUnit、Docker/MySQL/Redis 只读指标  
> 结论口径：只把业务断言通过且测试数据真实落库的样本计入正式结果。

## 1. 执行结论

本轮确认了推荐批量查询、RDP 路线简化、IM 快 ACK/幂等、轨迹 Batch 的核心优化链路均已存在并可工作，但不支持把结果表述成“全量 50 并发稳定通过”：在 1000 候选、C5、10 秒缓存命中测试中，虽然错误率为 0、P95 为 822.83 ms，却出现了 14.51 s 的单请求长尾。考虑服务器只有 2 vCPU、3.6 GiB 内存且无 Swap，本轮按安全原则停止 C10/C20/C30/C50 和 3 分钟正式阶段。

最重要的发现：

- 推荐 50/200/500/1000 候选的 C1 真实 miss 均为 0 错误，P95 分别为 246.83/570.91/255.81/998.06 ms；冷启动/偶发最大值使结果并非严格随候选数单调增长。
- 推荐实现使用候选、车队、指标、路线的批量查询；候选数增长没有表现为按候选逐条读取路线。最终 30 张卡片仍逐项查询关系状态，是一个有上限的残余 N+1，后续仍可批量化。
- RDP 单测 7/7 通过；40 条路线 A/B 中，平均 316.5 点经纯均匀采样为 60 点，经 RDP+上限为 5.2 点，Top5 对称差为 0。
- IM 10 RPS、20 秒共 201 次 ACK，0 错误，P95 35.97 ms；201 次相同 MsgKey 最终只落库 1 条消息。
- 轨迹 10 点、50 ms 模拟 RTT：Batch 整体迭代 167.44 ms，10 次单点请求整体迭代 801.15 ms，端到端耗时下降约 79.1%。
- 服务器部署存在严重配置缺陷：`TENCENT_IM_CALLBACK_TOKEN` 在 `.env` 和 backend 容器中均为空，运行服务把未解析占位符 `${TENCENT_IM_CALLBACK_TOKEN}` 当作 Token。IM 性能样本只能代表当前错误运行态，修复真实 Token 后必须复测。

## 2. 环境与安全边界

| 项目 | 值 |
|---|---:|
| CPU | 2 vCPU |
| 内存 | 3.6 GiB，无 Swap |
| backend JVM | Java 21，`-Xms256m -Xmx896m` |
| MySQL | 8.0.46 |
| Redis | 7.4.10 |
| 初始 backend 内存 | 346.1 MiB / 1.25 GiB |
| 收口 backend 内存 | 404.3 MiB / 1.25 GiB |

没有执行 DROP/TRUNCATE、Redis FLUSH、服务重启、Docker/Nginx/MySQL 配置修改。测试数据使用 `PERF` 标记及 `930000000000000000~930000000000009999` 独立 ID 段。最终启用 1000 条候选；这些数据未自动删除，便于复测且避免误删。

停止规则为 5xx > 2%、P95 > 5 s、服务无响应、连接池异常、CPU/OOM 风险。本轮虽未触发 P95 停止线，但 C5 出现 14.51 s 单请求和阶段结束后的在途等待，因此保守停止升档。

## 3. 推荐接口与 N+1 优化

### 3.1 代码链路核对

`MatchServiceImpl` 当前主链路为：

1. 一次读取最多 1000 条公开候选；
2. `findActiveTeamsByTripIds(candidateIds)` 批量读取车队；
3. `findByTripIds(candidateIds)` 批量读取推荐指标；
4. 只对粗排候选使用 `getMatchPolylines(routeIds)` 批量读取匹配路线；
5. 组装最终页时逐张卡片调用 `relationshipStatus`，最多 30 次。

因此“路线/车队/指标 N+1 已消除”成立；“整个推荐接口完全无 N+1”不成立，关系状态查询仍可优化。

### 3.2 C1 真实 cache-miss 候选规模

| 候选数 | HTTP 请求 | 错误率 | 平均 | P95 | 最大 |
|---:|---:|---:|---:|---:|---:|
| 50 | 37 | 0% | 214.86 ms | 246.83 ms | 280.59 ms |
| 200 | 21 | 0% | 295.19 ms | 570.91 ms | 959.19 ms |
| 500 | 21 | 0% | 302.27 ms | 255.81 ms | 1878.46 ms |
| 1000 | 17 | 0% | 429.24 ms | 998.06 ms | 3148.88 ms |

cache-miss 通过每次改变三位小数纬度构造不同缓存 key；没有清空 Redis。50 条首次脚本曾以 0.000001 改变坐标，但服务端 key 只保留三位小数，实际仍命中缓存；该文件保留为诊断证据，不计入上表。

### 3.3 cache-hit 与 C5

| 场景 | 请求 | 错误率 | 平均 | P95 | 最大 |
|---|---:|---:|---:|---:|---:|
| 50 候选，C1，30 s | 76 | 0% | 202.42 ms | 294.26 ms | 821.03 ms |
| 1000 候选，C5，10 s | 74 | 0% | 658.77 ms | 822.83 ms | 14505.09 ms |

服务端 1000 候选重算分段日志的 `totalMs` 约 208~594 ms。在 15 秒 TTL 同时失效时，5 个请求会并发重算，存在缓存击穿/惊群迹象。建议增加同 key single-flight、短互斥锁或 stale-while-revalidate。

## 4. RDP + 60 点路线匹配

配置已存在：`recommendation.route.rdp-epsilon=50m`、`max-points=60`。完整高德路线继续保存在 `trip_route.polyline`，匹配专用简化路线保存在 `match_polyline`，符合“不损失原始路线”的目标。

本地确定性 A/B（40 条合成弯折路线）：

| 指标 | Uniform | RDP + cap |
|---|---:|---:|
| 原始平均点数 | 316.5 | 316.5 |
| 简化平均点数 | 60.0 | 5.2 |
| 平均处理耗时 | 373.1 μs | 134.3 μs |
| P95 处理耗时 | 665.8 μs | 296.8 μs |
| 平均重合评分差 | 基准 | 0.00 |
| Top5 对称差 | 基准 | 0 |

RDP 平均耗时相对均匀采样低约 64.0%，但这是 JVM 内微基准，不等价于端到端接口性能。`RoutePolylineUtilsTest` 覆盖直线、拐角、最大点数、首尾点、小折线、重复/闭环等边界，6/6 通过；A/B 测试 1/1 通过。

发现页 50 候选 C1 对照：

| 排序 | 请求 | 错误率 | 平均 | P95 |
|---|---:|---:|---:|---:|
| RECOMMENDED | 50 | 0% | 107.35 ms | 136.98 ms |
| ROUTE_MATCH | 50 | 0% | 108.15 ms | 123.95 ms |

两组均使用真实 `referenceTripId=930000000000000000`。本轮没有在 200/500/1000 候选及更高并发下继续 ROUTE_MATCH，不能外推为高并发结论。

## 5. 腾讯 IM 回调

有效正式样本：MsgKey 固定、10 RPS、20 秒、201 次请求。

| 指标 | 结果 |
|---|---:|
| ACK 错误率 | 0% |
| 平均 ACK | 19.88 ms |
| P95 ACK | 35.97 ms |
| 最大 ACK | 42.16 ms |
| 相同 MsgKey 请求数 | 201 |
| 最终 chat_message 行数 | 1 |

这证明当前运行态的 AfterSend 回调可以快速 ACK，且 MsgKey 应用幂等有效。MsgId、MsgSeq+MsgRandom、20/50/100 RPS 和 50/100/300/600 突发未继续执行；原因是先发现验签配置缺陷，随后推荐 C5 又出现 14.5 秒长尾，不适合继续扩大现网压力。

配置缺陷必须先修：为服务器 `.env` 设置高熵 `TENCENT_IM_CALLBACK_TOKEN`，确认 backend 容器实际注入，重建/重启后用真实 Token 重跑。当前占位符是可预测字符串，不能作为生产安全边界。

## 6. 轨迹 Batch API

测试脚本在执行中修复了三项真实性问题：18 位行程 ID 必须以 JSON 字符串发送；原始批次不能声明不受支持的 `compression.type=NONE`；点间时间需要满足服务端 3 秒过滤门槛。修正前的结果文件只作为诊断，不计为通过。

最终有效对照为 10 点、C1、单次迭代、每次网络交互额外模拟 50 ms RTT：

| 模式 | HTTP 次数（含登录） | 业务断言 | 整体迭代耗时 |
|---|---:|---:|---:|
| Batch 10 | 2 | 10/10 accepted | 167.44 ms |
| Single 10 | 11 | 全部业务成功 | 801.15 ms |

Batch 相对单点整体耗时下降 `(801.15-167.44)/801.15 ≈ 79.1%`，主要收益来自减少 9 次网络往返。服务端 Batch 内部仍逐点调用 `uploadPoint`，并不是 JDBC 批量插入，因此不能宣称数据库写入批处理优化。

10/50/100/200 点、100/200 ms RTT、duplicate/overlap 全矩阵尚未执行。当前 PERF 行程累计 411 条轨迹记录，其中一部分来自脚本校准阶段并被服务端标记为 `TIME_REVERSED` 等无效点；正式 10 点样本已单独验证全部接收。

## 7. 系统指标与稳定性

收口读取：MySQL `Slow_queries=0`、`Threads_connected=11`、`Threads_running=2`；Redis `rejected_connections=0`、`keyspace_hits=2399`、`keyspace_misses=1086`。测试期间未看到 HikariPool、OutOfMemory 或 backend ERROR 日志。

这些是阶段前后快照，不是 Prometheus 连续时序，不能据此计算准确峰值 CPU/P95 资源占用。后续正式环境应接入持续采样。

## 8. 未执行项与下一步

以下项目明确未完成，不能写入简历为“已验证”：

- C10/C20/C30/C50 及每档 3 分钟正式测试；
- 200/500/1000 候选的 ROUTE_MATCH 并发矩阵；
- IM MsgId/fallback、20~100 RPS 与最高 600 突发；
- Track 10~200 点、50~200 ms RTT、duplicate/overlap 完整矩阵；
- MySQL 单请求 SQL 条数的运行时采样（本轮仅做代码路径和分段日志核对）。

建议顺序：先修 callback token 注入；再给推荐缓存加 single-flight；在独立压测机和可观测环境中重跑 3 分钟阶梯；最后才形成简历数字。

可如实使用的简历表述：

> 基于 k6 对路线推荐、IM 回调与轨迹批量上传进行专项性能验证；在 1000 候选 C1 cache-miss 下 P95 约 1.0 s、0 错误，RDP 将 40 条测试路线平均点数由 316.5 降至 5.2 且 Top5 结果不变；10 点轨迹在 50 ms 模拟 RTT 下 Batch 相比逐点上传端到端耗时下降约 79%。测试同时发现缓存同失效长尾与 IM callback token 注入缺陷，并据安全阈值停止继续升压。

## 9. 结果文件

有效结果位于 `performance-test/results/`：

- `recommend-c50-true-miss-c1.json`
- `recommend-c200-miss-c1.json`
- `recommend-c500-miss-c1.json`
- `recommend-c1000-miss-c1.json`
- `recommend-c50-hit-c1.json`
- `recommend-c1000-hit-c5.json`
- `discover-c50-recommended-c1.json`
- `discover-c50-route-match-c1.json`
- `im-msgkey-duplicate-rps10-runtime-placeholder.json`
- `track-batch10-all-accepted.json`
- `track-batch10-rtt50-comparison.json`
- `track-single10-rtt50-comparison.json`

其余带 `valid`/`accepted` 等中间名称但 checks 未全通过的文件属于脚本校准证据，报告没有把它们计为正式通过。

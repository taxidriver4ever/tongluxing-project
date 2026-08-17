# 结果口径

正式有效样本以报告第 9 节清单为准。以下文件是定位脚本真实性问题时保留的诊断结果，不得用于性能结论：

- `recommend-c50-miss-c1.json`：坐标变化小于缓存 key 的三位小数精度，实际不是 miss。
- `im-msgkey-duplicate-rps10.json`：签名映射与测试机时钟不重叠，没有发出有效请求。
- `im-msgkey-duplicate-rps10-valid.json`：使用容器中空 callback token 生成签名，业务验签失败。
- `track-batch10-rtt50-c1.json`：18 位 tripId 被转成 JavaScript Number 后精度丢失。
- `track-batch10-rtt50-c1-valid.json`：携带不受支持的 `compression.type=NONE`。
- `track-batch10-rtt50-c1-valid2.json`、`track-batch10-single-iteration-valid.json`、`track-batch10-valid-final.json`、`track-batch10-accepted.json`：用于校准时区、点间隔和最小位移，业务 checks 未全部通过。

保留这些文件是为了让测试过程可审计，避免只保留“好看”的成功样本。

# API 文档

默认服务地址：

```text
http://localhost:8080
```

## 健康检查

```http
GET /health
```

响应：

```json
{
  "status": "ok"
}
```

## 上游渠道

### 创建渠道

```http
POST /api/channels
Content-Type: application/json
```

```json
{
  "name": "Local Mock",
  "baseUrl": "http://localhost:8080/mock/v1",
  "apiKey": "local-dev-key",
  "model": "mock-model",
  "weight": 1
}
```

### 查询渠道

```http
GET /api/channels
```

### 修改渠道

```http
PUT /api/channels/{id}
Content-Type: application/json
```

```json
{
  "name": "Local Mock",
  "baseUrl": "http://localhost:8080/mock/v1",
  "apiKey": "new-key",
  "model": "mock-model",
  "weight": 2
}
```

### 启用或停用渠道

```http
PATCH /api/channels/{id}/status?status=ENABLED
PATCH /api/channels/{id}/status?status=DISABLED
```

### 删除渠道

```http
DELETE /api/channels/{id}
```

## 下游 API Key

### 创建 Key

```http
POST /api/keys
Content-Type: application/json
```

```json
{
  "name": "Demo Key",
  "quotaAmount": 100,
  "expiresAt": null
}
```

响应中的 `plainKey` 只在创建时返回。数据库保存的是 SHA-256 摘要，无法恢复完整 Key。

### 查询 Key

```http
GET /api/keys
```

列表响应不会返回完整 Key，只返回 `keyPrefix`。

### 修改状态

```http
PATCH /api/keys/{id}/status?status=ACTIVE
PATCH /api/keys/{id}/status?status=DISABLED
```

### 删除 Key

```http
DELETE /api/keys/{id}
```

## 统一 LLM 转发

```http
POST /v1/chat/completions
Authorization: Bearer <plainKey>
Content-Type: application/json
```

```json
{
  "model": "mock-model",
  "messages": [
    {
      "role": "user",
      "content": "hello"
    }
  ]
}
```

网关行为：

1. 校验下游 Key。
2. 根据 `model` 筛选渠道。
3. 按权重选择主渠道。
4. 上游失败时切换备用渠道。
5. 成功时扣减一次调用配额并记录日志。

每个下游 Key 默认每分钟最多 60 次请求。超过限制时返回 `429`，请求不会进入渠道选择。频率限制可通过环境变量 `GATEWAY_RATE_LIMIT_PER_MINUTE` 调整。

渠道连续失败 3 次后进入 30 秒熔断，期间不会再被选为候选渠道。成功请求会重置该渠道的连续失败计数。阈值和冷却时间可通过 `GATEWAY_CIRCUIT_FAILURE_THRESHOLD` 和 `GATEWAY_CIRCUIT_OPEN_MILLIS` 调整。

## 请求日志

```http
GET /api/logs
```

每条日志包含：

- `requestId`：一次上游尝试的请求 ID。
- `apiKeyId`：下游 Key。
- `channelId`：实际尝试的上游渠道。
- `model`：请求模型。
- `promptTokens`、`completionTokens`：上游返回的用量。
- `status`：`PENDING`、`SUCCESS` 或 `FAILED`。
- `latencyMs`：上游请求耗时。
- `errorCode`：失败时的错误标识。

### 用量汇总

```http
GET /api/logs/summary
```

响应：

```json
{
  "totalCalls": 3,
  "successCalls": 2,
  "failedCalls": 1,
  "pendingCalls": 0,
  "promptTokens": 20,
  "completionTokens": 30,
  "totalTokens": 50,
  "totalCost": 0,
  "averageLatencyMs": 45
}
```

空日志库返回全 0。`totalCost` 只汇总日志中已有的成本字段。

## 常见状态码

| 状态码 | 场景 |
|---|---|
| `400` | 参数错误、模型没有对应渠道 |
| `401` | 缺少或无效的下游 Key |
| `403` | Key 停用或过期 |
| `429` | Key 配额已用完或请求过于频繁 |
| `502` | 上游连接失败或所有渠道均不可用 |
| `503` | 没有启用的上游渠道 |

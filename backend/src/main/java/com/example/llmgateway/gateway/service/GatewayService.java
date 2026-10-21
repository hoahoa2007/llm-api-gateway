package com.example.llmgateway.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llmgateway.apikey.entity.DownstreamApiKey;
import com.example.llmgateway.apikey.mapper.DownstreamApiKeyMapper;
import com.example.llmgateway.channel.entity.UpstreamChannel;
import com.example.llmgateway.requestlog.entity.RequestLog;
import com.example.llmgateway.requestlog.mapper.RequestLogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class GatewayService {

    private final DownstreamApiKeyMapper keyMapper;
    private final GatewayRoutingService routingService;
    private final RateLimitService rateLimitService;
    private final CircuitBreakerService circuitBreakerService;
    private final RequestLogMapper requestLogMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public GatewayService(
            DownstreamApiKeyMapper keyMapper,
            GatewayRoutingService routingService,
            RateLimitService rateLimitService,
            CircuitBreakerService circuitBreakerService,
            RequestLogMapper requestLogMapper,
            ObjectMapper objectMapper
    ) {
        this.keyMapper = keyMapper;
        this.routingService = routingService;
        this.rateLimitService = rateLimitService;
        this.circuitBreakerService = circuitBreakerService;
        this.requestLogMapper = requestLogMapper;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<String> forwardChatCompletion(
            String authorization,
            JsonNode requestBody
    ) {
        DownstreamApiKey apiKey = authenticate(authorization);
        if (!rateLimitService.tryAcquire(apiKey.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "下游 API Key 请求过于频繁"
            );
        }
        List<UpstreamChannel> candidates = routingService.orderCandidates(
                requestBody.path("model").asText(null)
        );

        for (UpstreamChannel channel : candidates) {
            ResponseEntity<String> response = forwardToChannel(
                    apiKey,
                    channel,
                    requestBody
            );

            if (shouldFailover(response.getStatusCode().value())) {
                circuitBreakerService.recordFailure(channel.getId());
                continue;
            }

            if (response.getStatusCode().is2xxSuccessful()) {
                circuitBreakerService.recordSuccess(channel.getId());
                consumeQuota(apiKey);
            }
            return response;
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "所有上游渠道均不可用"
        );
    }

    private ResponseEntity<String> forwardToChannel(
            DownstreamApiKey apiKey,
            UpstreamChannel channel,
            JsonNode requestBody
    ) {
        String targetUrl = buildTargetUrl(channel);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(
                        requestBody.toString()
                ));

        if (StringUtils.hasText(channel.getApiKeyCiphertext())) {
            requestBuilder.header(
                    "Authorization",
                    "Bearer " + channel.getApiKeyCiphertext()
            );
        }

        RequestLog requestLog = createRequestLog(
                apiKey,
                channel,
                requestBody
        );
        long startedAt = System.nanoTime();

        try {
            HttpResponse<String> upstreamResponse = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            updateRequestLog(requestLog, upstreamResponse, startedAt);

            return ResponseEntity
                    .status(upstreamResponse.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(upstreamResponse.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            circuitBreakerService.recordFailure(channel.getId());
            failRequestLog(
                    requestLog,
                    "REQUEST_INTERRUPTED",
                    startedAt
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "上游请求被中断"
            );
        } catch (IOException exception) {
            failRequestLog(
                    requestLog,
                    "UPSTREAM_CONNECTION_FAILED",
                    startedAt
            );
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .build();
        }
    }

    private String buildTargetUrl(UpstreamChannel channel) {
        String baseUrl = channel.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "上游渠道地址为空"
            );
        }

        return baseUrl.endsWith("/")
                ? baseUrl + "chat/completions"
                : baseUrl + "/chat/completions";
    }

    private boolean shouldFailover(int statusCode) {
        return statusCode == 408
                || statusCode == 429
                || statusCode >= 500;
    }

    private DownstreamApiKey authenticate(String authorization) {
        if (!StringUtils.hasText(authorization)
                || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "缺少下游 API Key"
            );
        }

        String rawKey = authorization.substring("Bearer ".length()).trim();
        DownstreamApiKey apiKey = keyMapper.selectOne(
                new LambdaQueryWrapper<DownstreamApiKey>()
                        .eq(DownstreamApiKey::getKeyHash, sha256(rawKey))
                        .last("LIMIT 1")
        );

        if (apiKey == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "下游 API Key 无效"
            );
        }
        if (!"ACTIVE".equals(apiKey.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "下游 API Key 已停用"
            );
        }
        if (apiKey.getExpiresAt() != null
                && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "下游 API Key 已过期"
            );
        }

        BigDecimal quota = apiKey.getQuotaAmount() == null
                ? BigDecimal.ZERO
                : apiKey.getQuotaAmount();
        BigDecimal used = apiKey.getUsedAmount() == null
                ? BigDecimal.ZERO
                : apiKey.getUsedAmount();

        if (quota.compareTo(BigDecimal.ZERO) > 0
                && used.compareTo(quota) >= 0) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "下游 API Key 配额已用完"
            );
        }
        return apiKey;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private RequestLog createRequestLog(
            DownstreamApiKey apiKey,
            UpstreamChannel channel,
            JsonNode requestBody
    ) {
        RequestLog log = new RequestLog();
        log.setRequestId(UUID.randomUUID().toString());
        log.setApiKeyId(apiKey.getId());
        log.setChannelId(channel.getId());
        log.setModel(requestBody.path("model").asText(channel.getModel()));
        log.setCost(BigDecimal.ZERO);
        log.setStatus("PENDING");
        log.setCreatedAt(LocalDateTime.now());
        requestLogMapper.insert(log);
        return log;
    }

    private void updateRequestLog(
            RequestLog log,
            HttpResponse<String> response,
            long startedAt
    ) {
        log.setLatencyMs(elapsedMillis(startedAt));

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.setStatus("SUCCESS");
            readUsage(log, response.body());
        } else {
            log.setStatus("FAILED");
            log.setErrorCode("UPSTREAM_" + response.statusCode());
        }
        requestLogMapper.updateById(log);
    }

    private void failRequestLog(
            RequestLog log,
            String errorCode,
            long startedAt
    ) {
        log.setStatus("FAILED");
        log.setErrorCode(errorCode);
        log.setLatencyMs(elapsedMillis(startedAt));
        requestLogMapper.updateById(log);
    }

    private void readUsage(RequestLog log, String responseBody) {
        try {
            JsonNode usage = objectMapper.readTree(responseBody).path("usage");
            log.setPromptTokens(usage.path("prompt_tokens").asInt(0));
            log.setCompletionTokens(
                    usage.path("completion_tokens").asInt(0)
            );
        } catch (IOException ignored) {
            log.setPromptTokens(0);
            log.setCompletionTokens(0);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(
                System.nanoTime() - startedAt
        ).toMillis();
    }

    private void consumeQuota(DownstreamApiKey apiKey) {
        int updated = keyMapper.consumeQuota(apiKey.getId());
        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "下游 API Key 配额已用完"
            );
        }
    }
}

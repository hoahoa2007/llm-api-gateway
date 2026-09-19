package com.example.llmgateway.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llmgateway.apikey.entity.DownstreamApiKey;
import com.example.llmgateway.apikey.mapper.DownstreamApiKeyMapper;
import com.example.llmgateway.channel.entity.UpstreamChannel;
import com.example.llmgateway.channel.mapper.UpstreamChannelMapper;
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
import java.util.UUID;

@Service
public class GatewayService {

    private final DownstreamApiKeyMapper keyMapper;
    private final UpstreamChannelMapper channelMapper;
    private final RequestLogMapper requestLogMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public GatewayService(
            DownstreamApiKeyMapper keyMapper,
            UpstreamChannelMapper channelMapper,
            RequestLogMapper requestLogMapper,
            ObjectMapper objectMapper
    ) {
        this.keyMapper = keyMapper;
        this.channelMapper = channelMapper;
        this.requestLogMapper = requestLogMapper;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<String> forwardChatCompletion(
            String authorization,
            JsonNode requestBody
    ) {
        DownstreamApiKey apiKey = authenticate(authorization);
        UpstreamChannel channel = selectChannel();

        String baseUrl = channel.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "上游渠道地址为空"
            );
        }

        String targetUrl = baseUrl.endsWith("/")
                ? baseUrl + "chat/completions"
                : baseUrl + "/chat/completions";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(
                        requestBody.toString()
                ));

        RequestLog requestLog = createRequestLog(
                apiKey,
                channel,
                requestBody
        );
        long startedAt = System.nanoTime();

        if (StringUtils.hasText(channel.getApiKeyCiphertext())) {
            requestBuilder.header(
                    "Authorization",
                    "Bearer " + channel.getApiKeyCiphertext()
            );
        }

        try {
            HttpResponse<String> upstreamResponse = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            updateRequestLog(
                    requestLog,
                    upstreamResponse,
                    startedAt
            );

            return ResponseEntity
                    .status(upstreamResponse.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(upstreamResponse.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
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
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "无法连接上游渠道"
            );
        }
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
        return apiKey;
    }

    private UpstreamChannel selectChannel() {
        UpstreamChannel channel = channelMapper.selectOne(
                new LambdaQueryWrapper<UpstreamChannel>()
                        .eq(UpstreamChannel::getStatus, "ENABLED")
                        .orderByAsc(UpstreamChannel::getId)
                        .last("LIMIT 1")
        );

        if (channel == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "没有可用的上游渠道"
            );
        }
        return channel;
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
}

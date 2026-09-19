package com.example.llmgateway.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llmgateway.apikey.entity.DownstreamApiKey;
import com.example.llmgateway.apikey.mapper.DownstreamApiKeyMapper;
import com.example.llmgateway.channel.entity.UpstreamChannel;
import com.example.llmgateway.channel.mapper.UpstreamChannelMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.HexFormat;

@Service
public class GatewayService {

    private final DownstreamApiKeyMapper keyMapper;
    private final UpstreamChannelMapper channelMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public GatewayService(
            DownstreamApiKeyMapper keyMapper,
            UpstreamChannelMapper channelMapper
    ) {
        this.keyMapper = keyMapper;
        this.channelMapper = channelMapper;
    }

    public ResponseEntity<String> forwardChatCompletion(
            String authorization,
            JsonNode requestBody
    ) {
        authenticate(authorization);
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

            return ResponseEntity
                    .status(upstreamResponse.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(upstreamResponse.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "上游请求被中断"
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "无法连接上游渠道"
            );
        }
    }

    private void authenticate(String authorization) {
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
}

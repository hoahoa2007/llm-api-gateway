package com.example.llmgateway.requestlog.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RequestLogResponse(
        Long id,
        String requestId,
        Long apiKeyId,
        Long channelId,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        BigDecimal cost,
        String status,
        Long latencyMs,
        String errorCode,
        LocalDateTime createdAt
) {
}

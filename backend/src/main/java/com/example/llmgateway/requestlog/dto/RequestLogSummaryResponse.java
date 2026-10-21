package com.example.llmgateway.requestlog.dto;

import java.math.BigDecimal;

public record RequestLogSummaryResponse(
        long totalCalls,
        long successCalls,
        long failedCalls,
        long pendingCalls,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal totalCost,
        long averageLatencyMs
) {
}

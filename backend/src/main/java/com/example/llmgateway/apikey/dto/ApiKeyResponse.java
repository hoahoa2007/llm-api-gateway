package com.example.llmgateway.apikey.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ApiKeyResponse(
        Long id,
        String name,
        String keyPrefix,
        String status,
        BigDecimal quotaAmount,
        BigDecimal usedAmount,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}

package com.example.llmgateway.channel.dto;

import java.time.LocalDateTime;

public record ChannelResponse(
        Long id,
        String name,
        String baseUrl,
        String model,
        Integer weight,
        String status,
        String healthStatus,
        boolean apiKeyConfigured,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

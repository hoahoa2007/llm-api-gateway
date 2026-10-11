package com.example.llmgateway.apikey.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ApiKeyCreateRequest(
        @NotBlank(message = "Key 名称不能为空")
        @Size(max = 100, message = "Key 名称不能超过 100 个字符")
        String name,

        @DecimalMin(value = "0", message = "配额不能小于 0")
        BigDecimal quotaAmount,

        @Future(message = "过期时间必须晚于当前时间")
        LocalDateTime expiresAt
) {
}
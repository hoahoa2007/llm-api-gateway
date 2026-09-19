package com.example.llmgateway.apikey.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("downstream_api_key")
public class DownstreamApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String keyHash;
    private String keyPrefix;
    private String status;

    private BigDecimal quotaAmount;
    private BigDecimal usedAmount;

    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
package com.example.llmgateway.requestlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("request_log")
public class RequestLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;
    private Long apiKeyId;
    private Long channelId;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private BigDecimal cost;
    private String status;
    private Long latencyMs;
    private String errorCode;
    private LocalDateTime createdAt;
}

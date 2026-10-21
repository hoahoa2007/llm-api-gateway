package com.example.llmgateway.requestlog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llmgateway.requestlog.dto.RequestLogResponse;
import com.example.llmgateway.requestlog.dto.RequestLogSummaryResponse;
import com.example.llmgateway.requestlog.entity.RequestLog;
import com.example.llmgateway.requestlog.mapper.RequestLogMapper;
import com.example.llmgateway.requestlog.service.RequestLogService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RequestLogServiceImpl implements RequestLogService {

    private final RequestLogMapper requestLogMapper;

    public RequestLogServiceImpl(RequestLogMapper requestLogMapper) {
        this.requestLogMapper = requestLogMapper;
    }

    @Override
    public List<RequestLogResponse> listLogs() {
        LambdaQueryWrapper<RequestLog> query =
                new LambdaQueryWrapper<RequestLog>()
                        .orderByDesc(RequestLog::getCreatedAt);

        return requestLogMapper.selectList(query)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public RequestLogSummaryResponse summary() {
        List<RequestLog> logs = requestLogMapper.selectList(null);

        long successCalls = 0;
        long failedCalls = 0;
        long pendingCalls = 0;
        long promptTokens = 0;
        long completionTokens = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        long latencySum = 0;
        long latencyCount = 0;

        for (RequestLog log : logs) {
            if ("SUCCESS".equals(log.getStatus())) {
                successCalls++;
            } else if ("FAILED".equals(log.getStatus())) {
                failedCalls++;
            } else if ("PENDING".equals(log.getStatus())) {
                pendingCalls++;
            }

            promptTokens += log.getPromptTokens() == null
                    ? 0
                    : log.getPromptTokens();
            completionTokens += log.getCompletionTokens() == null
                    ? 0
                    : log.getCompletionTokens();
            if (log.getCost() != null) {
                totalCost = totalCost.add(log.getCost());
            }
            if (log.getLatencyMs() != null) {
                latencySum += log.getLatencyMs();
                latencyCount++;
            }
        }

        long averageLatencyMs = latencyCount == 0
                ? 0
                : Math.round((double) latencySum / latencyCount);

        return new RequestLogSummaryResponse(
                logs.size(),
                successCalls,
                failedCalls,
                pendingCalls,
                promptTokens,
                completionTokens,
                promptTokens + completionTokens,
                totalCost,
                averageLatencyMs
        );
    }

    private RequestLogResponse toResponse(RequestLog log) {
        return new RequestLogResponse(
                log.getId(),
                log.getRequestId(),
                log.getApiKeyId(),
                log.getChannelId(),
                log.getModel(),
                log.getPromptTokens(),
                log.getCompletionTokens(),
                log.getCost(),
                log.getStatus(),
                log.getLatencyMs(),
                log.getErrorCode(),
                log.getCreatedAt()
        );
    }
}

package com.example.llmgateway.requestlog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llmgateway.requestlog.dto.RequestLogResponse;
import com.example.llmgateway.requestlog.entity.RequestLog;
import com.example.llmgateway.requestlog.mapper.RequestLogMapper;
import com.example.llmgateway.requestlog.service.RequestLogService;
import org.springframework.stereotype.Service;

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

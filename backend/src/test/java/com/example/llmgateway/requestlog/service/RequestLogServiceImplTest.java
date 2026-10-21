package com.example.llmgateway.requestlog.service;

import com.example.llmgateway.requestlog.dto.RequestLogSummaryResponse;
import com.example.llmgateway.requestlog.entity.RequestLog;
import com.example.llmgateway.requestlog.mapper.RequestLogMapper;
import com.example.llmgateway.requestlog.service.impl.RequestLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestLogServiceImplTest {

    private final RequestLogMapper requestLogMapper =
            mock(RequestLogMapper.class);
    private RequestLogServiceImpl requestLogService;

    @BeforeEach
    void setUp() {
        requestLogService = new RequestLogServiceImpl(requestLogMapper);
    }

    @Test
    void shouldReturnZeroSummaryForEmptyLog() {
        when(requestLogMapper.selectList(null)).thenReturn(List.of());

        RequestLogSummaryResponse summary = requestLogService.summary();

        assertEquals(0, summary.totalCalls());
        assertEquals(0, summary.successCalls());
        assertEquals(0, summary.failedCalls());
        assertEquals(0, summary.pendingCalls());
        assertEquals(0, summary.totalTokens());
        assertEquals(BigDecimal.ZERO, summary.totalCost());
        assertEquals(0, summary.averageLatencyMs());
    }

    @Test
    void shouldAggregateCountsTokensCostAndLatency() {
        when(requestLogMapper.selectList(null)).thenReturn(List.of(
                log("SUCCESS", 8, 12, "1.50", 10L),
                log("SUCCESS", 4, 6, "0.50", 20L),
                log("FAILED", null, null, null, 30L),
                log("PENDING", null, null, "0", 40L)
        ));

        RequestLogSummaryResponse summary = requestLogService.summary();

        assertEquals(4, summary.totalCalls());
        assertEquals(2, summary.successCalls());
        assertEquals(1, summary.failedCalls());
        assertEquals(1, summary.pendingCalls());
        assertEquals(12, summary.promptTokens());
        assertEquals(18, summary.completionTokens());
        assertEquals(30, summary.totalTokens());
        assertEquals(0, new BigDecimal("2.00").compareTo(summary.totalCost()));
        assertEquals(25, summary.averageLatencyMs());
    }

    private RequestLog log(
            String status,
            Integer promptTokens,
            Integer completionTokens,
            String cost,
            Long latencyMs
    ) {
        RequestLog log = new RequestLog();
        log.setStatus(status);
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setCost(cost == null ? null : new BigDecimal(cost));
        log.setLatencyMs(latencyMs);
        return log;
    }
}

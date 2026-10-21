package com.example.llmgateway.requestlog.controller;

import com.example.llmgateway.requestlog.dto.RequestLogResponse;
import com.example.llmgateway.requestlog.dto.RequestLogSummaryResponse;
import com.example.llmgateway.requestlog.service.RequestLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class RequestLogController {

    private final RequestLogService requestLogService;

    public RequestLogController(RequestLogService requestLogService) {
        this.requestLogService = requestLogService;
    }

    @GetMapping
    public List<RequestLogResponse> list() {
        return requestLogService.listLogs();
    }

    @GetMapping("/summary")
    public RequestLogSummaryResponse summary() {
        return requestLogService.summary();
    }
}

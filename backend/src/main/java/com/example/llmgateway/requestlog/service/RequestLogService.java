package com.example.llmgateway.requestlog.service;

import com.example.llmgateway.requestlog.dto.RequestLogResponse;
import com.example.llmgateway.requestlog.dto.RequestLogSummaryResponse;

import java.util.List;

public interface RequestLogService {

    List<RequestLogResponse> listLogs();

    RequestLogSummaryResponse summary();
}

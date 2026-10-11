package com.example.llmgateway.apikey.service;

import com.example.llmgateway.apikey.dto.ApiKeyCreateRequest;
import com.example.llmgateway.apikey.dto.ApiKeyCreateResponse;
import com.example.llmgateway.apikey.dto.ApiKeyResponse;

import java.util.List;

public interface DownstreamApiKeyService {

    ApiKeyCreateResponse createKey(ApiKeyCreateRequest request);

    List<ApiKeyResponse> listKeys();

    void updateStatus(Long id, String status);

    void deleteKey(Long id);
}

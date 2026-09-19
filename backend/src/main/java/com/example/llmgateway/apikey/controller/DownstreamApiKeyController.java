package com.example.llmgateway.apikey.controller;

import com.example.llmgateway.apikey.dto.ApiKeyCreateRequest;
import com.example.llmgateway.apikey.dto.ApiKeyCreateResponse;
import com.example.llmgateway.apikey.dto.ApiKeyResponse;
import com.example.llmgateway.apikey.service.DownstreamApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/keys")
public class DownstreamApiKeyController {

    private final DownstreamApiKeyService keyService;

    public DownstreamApiKeyController(DownstreamApiKeyService keyService) {
        this.keyService = keyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyCreateResponse create(
            @Valid @RequestBody ApiKeyCreateRequest request
    ) {
        return keyService.createKey(request);
    }

    @GetMapping
    public List<ApiKeyResponse> list() {
        return keyService.listKeys();
    }
}

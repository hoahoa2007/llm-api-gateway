package com.example.llmgateway.apikey.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llmgateway.apikey.dto.ApiKeyCreateRequest;
import com.example.llmgateway.apikey.dto.ApiKeyCreateResponse;
import com.example.llmgateway.apikey.dto.ApiKeyResponse;
import com.example.llmgateway.apikey.entity.DownstreamApiKey;
import com.example.llmgateway.apikey.mapper.DownstreamApiKeyMapper;
import com.example.llmgateway.apikey.service.DownstreamApiKeyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class DownstreamApiKeyServiceImpl implements DownstreamApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DownstreamApiKeyMapper keyMapper;

    public DownstreamApiKeyServiceImpl(DownstreamApiKeyMapper keyMapper) {
        this.keyMapper = keyMapper;
    }

    @Override
    @Transactional
    public ApiKeyCreateResponse createKey(ApiKeyCreateRequest request) {
        String rawKey = generateRawKey();
        LocalDateTime now = LocalDateTime.now();

        DownstreamApiKey entity = new DownstreamApiKey();
        entity.setName(request.name().trim());
        entity.setKeyHash(sha256(rawKey));
        entity.setKeyPrefix(rawKey.substring(0, 12));
        entity.setStatus("ACTIVE");
        entity.setQuotaAmount(
                request.quotaAmount() == null
                        ? BigDecimal.ZERO
                        : request.quotaAmount()
        );
        entity.setUsedAmount(BigDecimal.ZERO);
        entity.setExpiresAt(request.expiresAt());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        keyMapper.insert(entity);

        return new ApiKeyCreateResponse(
                entity.getId(),
                entity.getName(),
                entity.getKeyPrefix(),
                rawKey,
                entity.getStatus(),
                entity.getQuotaAmount(),
                entity.getUsedAmount(),
                entity.getExpiresAt(),
                entity.getCreatedAt()
        );
    }

    @Override
    public List<ApiKeyResponse> listKeys() {
        LambdaQueryWrapper<DownstreamApiKey> query =
                new LambdaQueryWrapper<DownstreamApiKey>()
                        .orderByDesc(DownstreamApiKey::getCreatedAt);

        return keyMapper.selectList(query)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ApiKeyResponse toResponse(DownstreamApiKey entity) {
        return new ApiKeyResponse(
                entity.getId(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getStatus(),
                entity.getQuotaAmount(),
                entity.getUsedAmount(),
                entity.getExpiresAt(),
                entity.getCreatedAt()
        );
    }

    private String generateRawKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "sk-" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

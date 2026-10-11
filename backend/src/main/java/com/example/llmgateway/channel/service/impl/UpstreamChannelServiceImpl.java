package com.example.llmgateway.channel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llmgateway.channel.dto.ChannelCreateRequest;
import com.example.llmgateway.channel.dto.ChannelResponse;
import com.example.llmgateway.channel.dto.ChannelUpdateRequest;
import com.example.llmgateway.channel.entity.UpstreamChannel;
import com.example.llmgateway.channel.mapper.UpstreamChannelMapper;
import com.example.llmgateway.channel.service.UpstreamChannelService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UpstreamChannelServiceImpl implements UpstreamChannelService {

    private final UpstreamChannelMapper channelMapper;

    public UpstreamChannelServiceImpl(UpstreamChannelMapper channelMapper) {
        this.channelMapper = channelMapper;
    }

    @Override
    @Transactional
    public ChannelResponse createChannel(ChannelCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();

        UpstreamChannel channel = new UpstreamChannel();
        channel.setName(request.name().trim());
        channel.setBaseUrl(request.baseUrl().trim());
        channel.setApiKeyCiphertext(request.apiKey());
        channel.setModel(request.model().trim());
        channel.setWeight(request.weight() == null ? 1 : request.weight());
        channel.setStatus("ENABLED");
        channel.setHealthStatus("UNKNOWN");
        channel.setCreatedAt(now);
        channel.setUpdatedAt(now);

        channelMapper.insert(channel);
        return toResponse(channel);
    }

    @Override
    public List<ChannelResponse> listChannels() {
        LambdaQueryWrapper<UpstreamChannel> query =
                new LambdaQueryWrapper<UpstreamChannel>()
                        .orderByDesc(UpstreamChannel::getCreatedAt);

        return channelMapper.selectList(query)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ChannelResponse updateChannel(
            Long id,
            ChannelUpdateRequest request
    ) {
        UpstreamChannel channel = requireChannel(id);
        channel.setName(request.name().trim());
        channel.setBaseUrl(request.baseUrl().trim());
        channel.setModel(request.model().trim());
        channel.setWeight(request.weight() == null ? 1 : request.weight());

        if (StringUtils.hasText(request.apiKey())) {
            channel.setApiKeyCiphertext(request.apiKey());
        }

        channel.setUpdatedAt(LocalDateTime.now());
        channelMapper.updateById(channel);
        return toResponse(channel);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status) {
        String normalized = status == null ? "" : status.toUpperCase();

        if (!"ENABLED".equals(normalized)
                && !"DISABLED".equals(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "渠道状态只能是 ENABLED 或 DISABLED"
            );
        }

        UpstreamChannel channel = requireChannel(id);
        channel.setStatus(normalized);
        channel.setUpdatedAt(LocalDateTime.now());
        channelMapper.updateById(channel);
    }

    @Override
    @Transactional
    public void deleteChannel(Long id) {
        requireChannel(id);
        channelMapper.deleteById(id);
    }

    private UpstreamChannel requireChannel(Long id) {
        UpstreamChannel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "渠道不存在"
            );
        }
        return channel;
    }

    private ChannelResponse toResponse(UpstreamChannel channel) {
        return new ChannelResponse(
                channel.getId(),
                channel.getName(),
                channel.getBaseUrl(),
                channel.getModel(),
                channel.getWeight(),
                channel.getStatus(),
                channel.getHealthStatus(),
                channel.getApiKeyCiphertext() != null
                        && !channel.getApiKeyCiphertext().isBlank(),
                channel.getCreatedAt(),
                channel.getUpdatedAt()
        );
    }
}

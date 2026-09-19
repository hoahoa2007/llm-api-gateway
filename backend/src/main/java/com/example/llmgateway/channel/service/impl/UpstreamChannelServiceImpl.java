package com.example.llmgateway.channel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llmgateway.channel.dto.ChannelCreateRequest;
import com.example.llmgateway.channel.dto.ChannelResponse;
import com.example.llmgateway.channel.entity.UpstreamChannel;
import com.example.llmgateway.channel.mapper.UpstreamChannelMapper;
import com.example.llmgateway.channel.service.UpstreamChannelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public void updateStatus(Long id, String status) {
        String normalized = status == null ? "" : status.toUpperCase();

        if (!"ENABLED".equals(normalized)
                && !"DISABLED".equals(normalized)) {
            throw new IllegalArgumentException(
                    "渠道状态只能是 ENABLED 或 DISABLED"
            );
        }

        UpstreamChannel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new IllegalArgumentException("渠道不存在");
        }

        channel.setStatus(normalized);
        channel.setUpdatedAt(LocalDateTime.now());
        channelMapper.updateById(channel);
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
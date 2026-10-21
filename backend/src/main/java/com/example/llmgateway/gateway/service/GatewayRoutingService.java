package com.example.llmgateway.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llmgateway.channel.entity.UpstreamChannel;
import com.example.llmgateway.channel.mapper.UpstreamChannelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GatewayRoutingService {

    private final UpstreamChannelMapper channelMapper;
    private final CircuitBreakerService circuitBreakerService;
    private final AtomicInteger sequence = new AtomicInteger();

    public GatewayRoutingService(
            UpstreamChannelMapper channelMapper,
            CircuitBreakerService circuitBreakerService
    ) {
        this.channelMapper = channelMapper;
        this.circuitBreakerService = circuitBreakerService;
    }

    public List<UpstreamChannel> orderCandidates(String requestedModel) {
        List<UpstreamChannel> candidates = loadEnabledChannels();

        if (StringUtils.hasText(requestedModel)) {
            candidates = candidates.stream()
                    .filter(channel -> requestedModel.equals(channel.getModel()))
                    .toList();

            if (candidates.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "没有支持模型 " + requestedModel + " 的上游渠道"
                );
            }
        }

        if (candidates.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "没有可用的上游渠道"
            );
        }

        List<UpstreamChannel> ordered = new ArrayList<>(candidates.size());
        ordered.add(selectPrimary(candidates));

        candidates.stream()
                .filter(channel -> !channel.getId().equals(ordered.get(0).getId()))
                .sorted(Comparator
                        .comparingInt(this::normalizedWeight)
                        .reversed()
                        .thenComparing(UpstreamChannel::getId))
                .forEach(ordered::add);

        return List.copyOf(ordered);
    }

    private List<UpstreamChannel> loadEnabledChannels() {
        return channelMapper.selectList(
                new LambdaQueryWrapper<UpstreamChannel>()
                        .eq(UpstreamChannel::getStatus, "ENABLED")
                        .orderByAsc(UpstreamChannel::getId)
        ).stream()
                .filter(channel -> circuitBreakerService.isAvailable(
                        channel.getId()
                ))
                .toList();
    }

    private UpstreamChannel selectPrimary(List<UpstreamChannel> candidates) {
        int totalWeight = candidates.stream()
                .mapToInt(this::normalizedWeight)
                .sum();
        int slot = Math.floorMod(sequence.getAndIncrement(), totalWeight);
        int cumulativeWeight = 0;

        for (UpstreamChannel candidate : candidates) {
            cumulativeWeight += normalizedWeight(candidate);
            if (slot < cumulativeWeight) {
                return candidate;
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    private int normalizedWeight(UpstreamChannel channel) {
        return channel.getWeight() == null || channel.getWeight() < 1
                ? 1
                : channel.getWeight();
    }
}

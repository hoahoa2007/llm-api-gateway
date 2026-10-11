package com.example.llmgateway.gateway.service;

import com.example.llmgateway.channel.entity.UpstreamChannel;
import com.example.llmgateway.channel.mapper.UpstreamChannelMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayRoutingServiceTest {

    private final UpstreamChannelMapper channelMapper =
            mock(UpstreamChannelMapper.class);
    private final GatewayRoutingService routingService =
            new GatewayRoutingService(channelMapper);

    @Test
    void shouldSelectPrimaryChannelByWeight() {
        UpstreamChannel first = channel(1L, "mock-model", 1);
        UpstreamChannel second = channel(2L, "mock-model", 3);
        when(channelMapper.selectList(any()))
                .thenReturn(List.of(first, second));

        List<Long> primaryIds = List.of(
                routingService.orderCandidates("mock-model").get(0).getId(),
                routingService.orderCandidates("mock-model").get(0).getId(),
                routingService.orderCandidates("mock-model").get(0).getId(),
                routingService.orderCandidates("mock-model").get(0).getId()
        );

        assertEquals(List.of(1L, 2L, 2L, 2L), primaryIds);
    }

    @Test
    void shouldKeepOtherChannelsAsFailoverCandidates() {
        UpstreamChannel first = channel(1L, "mock-model", 3);
        UpstreamChannel second = channel(2L, "mock-model", 1);
        when(channelMapper.selectList(any()))
                .thenReturn(List.of(first, second));

        List<Long> candidateIds = routingService.orderCandidates("mock-model")
                .stream()
                .map(UpstreamChannel::getId)
                .toList();

        assertEquals(List.of(1L, 2L), candidateIds);
    }

    @Test
    void shouldFilterChannelsByRequestedModel() {
        UpstreamChannel matching = channel(1L, "model-a", 1);
        UpstreamChannel other = channel(2L, "model-b", 1);
        when(channelMapper.selectList(any()))
                .thenReturn(List.of(matching, other));

        List<UpstreamChannel> candidates =
                routingService.orderCandidates("model-a");

        assertEquals(1, candidates.size());
        assertEquals(1L, candidates.get(0).getId());
    }

    @Test
    void shouldRejectUnsupportedModel() {
        when(channelMapper.selectList(any()))
                .thenReturn(List.of(channel(1L, "model-a", 1)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> routingService.orderCandidates("model-x")
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    private UpstreamChannel channel(
            Long id,
            String model,
            Integer weight
    ) {
        UpstreamChannel channel = new UpstreamChannel();
        channel.setId(id);
        channel.setModel(model);
        channel.setWeight(weight);
        channel.setStatus("ENABLED");
        channel.setBaseUrl("http://localhost:8080/mock/v1");
        return channel;
    }
}

package com.example.llmgateway.gateway.service;

import com.example.llmgateway.apikey.entity.DownstreamApiKey;
import com.example.llmgateway.apikey.mapper.DownstreamApiKeyMapper;
import com.example.llmgateway.channel.entity.UpstreamChannel;
import com.example.llmgateway.requestlog.entity.RequestLog;
import com.example.llmgateway.requestlog.mapper.RequestLogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GatewayServiceTest {

    private final DownstreamApiKeyMapper keyMapper =
            mock(DownstreamApiKeyMapper.class);
    private final GatewayRoutingService routingService =
            mock(GatewayRoutingService.class);
    private final RateLimitService rateLimitService =
            mock(RateLimitService.class);
    private final CircuitBreakerService circuitBreakerService =
            mock(CircuitBreakerService.class);
    private final RequestLogMapper requestLogMapper =
            mock(RequestLogMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GatewayService gatewayService = new GatewayService(
            keyMapper,
            routingService,
            rateLimitService,
            circuitBreakerService,
            requestLogMapper,
            objectMapper
    );

    private HttpServer firstServer;
    private HttpServer secondServer;

    @AfterEach
    void tearDown() {
        stop(firstServer);
        stop(secondServer);
    }

    @Test
    void shouldForwardRequestAndConsumeQuota() throws IOException {
        firstServer = startServer(
                200,
                """
                {"id":"chatcmpl-1","usage":{"prompt_tokens":8,"completion_tokens":12}}
                """
        );
        DownstreamApiKey apiKey = apiKey(4L, 5, 0);
        UpstreamChannel channel = channel(1L, firstServer.getAddress().getPort());

        when(keyMapper.selectOne(any())).thenReturn(apiKey);
        when(keyMapper.consumeQuota(4L)).thenReturn(1);
        when(rateLimitService.tryAcquire(4L)).thenReturn(true);
        when(requestLogMapper.insert(any(RequestLog.class))).thenReturn(1);
        when(requestLogMapper.updateById(any(RequestLog.class))).thenReturn(1);
        when(routingService.orderCandidates("mock-model"))
                .thenReturn(List.of(channel));

        ResponseEntity<String> response = gatewayService.forwardChatCompletion(
                "Bearer test-key",
                requestBody()
        );

        assertEquals(200, response.getStatusCode().value());
        verify(keyMapper).consumeQuota(4L);
        verify(circuitBreakerService).recordSuccess(1L);
        verify(requestLogMapper).insert(any(RequestLog.class));
        verify(requestLogMapper).updateById(any(RequestLog.class));
    }

    @Test
    void shouldFailoverToNextChannel() throws IOException {
        firstServer = startServer(503, "{\"error\":\"temporary\"}");
        secondServer = startServer(
                200,
                """
                {"id":"chatcmpl-2","usage":{"prompt_tokens":3,"completion_tokens":5}}
                """
        );
        DownstreamApiKey apiKey = apiKey(4L, 5, 0);
        UpstreamChannel first = channel(1L, firstServer.getAddress().getPort());
        UpstreamChannel second = channel(2L, secondServer.getAddress().getPort());

        when(keyMapper.selectOne(any())).thenReturn(apiKey);
        when(keyMapper.consumeQuota(4L)).thenReturn(1);
        when(rateLimitService.tryAcquire(4L)).thenReturn(true);
        when(requestLogMapper.insert(any(RequestLog.class))).thenReturn(1);
        when(requestLogMapper.updateById(any(RequestLog.class))).thenReturn(1);
        when(routingService.orderCandidates("mock-model"))
                .thenReturn(List.of(first, second));

        ResponseEntity<String> response = gatewayService.forwardChatCompletion(
                "Bearer test-key",
                requestBody()
        );

        assertEquals(200, response.getStatusCode().value());
        verify(requestLogMapper, times(2)).insert(any(RequestLog.class));
        verify(requestLogMapper, times(2)).updateById(any(RequestLog.class));
        verify(circuitBreakerService).recordFailure(1L);
        verify(circuitBreakerService).recordSuccess(2L);
        verify(keyMapper).consumeQuota(4L);
    }

    @Test
    void shouldNotFailoverForClientRequestError() throws IOException {
        firstServer = startServer(400, "{\"error\":\"bad request\"}");
        secondServer = startServer(200, "{\"id\":\"should-not-be-used\"}");
        DownstreamApiKey apiKey = apiKey(4L, 5, 0);
        UpstreamChannel first = channel(1L, firstServer.getAddress().getPort());
        UpstreamChannel second = channel(2L, secondServer.getAddress().getPort());

        when(keyMapper.selectOne(any())).thenReturn(apiKey);
        when(rateLimitService.tryAcquire(4L)).thenReturn(true);
        when(requestLogMapper.insert(any(RequestLog.class))).thenReturn(1);
        when(requestLogMapper.updateById(any(RequestLog.class))).thenReturn(1);
        when(routingService.orderCandidates("mock-model"))
                .thenReturn(List.of(first, second));

        ResponseEntity<String> response = gatewayService.forwardChatCompletion(
                "Bearer test-key",
                requestBody()
        );

        assertEquals(400, response.getStatusCode().value());
        verify(requestLogMapper).insert(any(RequestLog.class));
        verify(requestLogMapper).updateById(any(RequestLog.class));
        verify(keyMapper, times(0)).consumeQuota(4L);
        verifyNoInteractions(circuitBreakerService);
    }

    @Test
    void shouldRejectExhaustedQuotaBeforeRouting() throws IOException {
        DownstreamApiKey apiKey = apiKey(4L, 1, 1);
        when(keyMapper.selectOne(any())).thenReturn(apiKey);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gatewayService.forwardChatCompletion(
                        "Bearer test-key",
                        requestBody()
                )
        );

        assertEquals(429, exception.getStatusCode().value());
        verifyNoInteractions(routingService);
        verifyNoInteractions(rateLimitService);
    }

    @Test
    void shouldRejectRateLimitedRequestBeforeRouting() throws IOException {
        DownstreamApiKey apiKey = apiKey(4L, 5, 0);
        when(keyMapper.selectOne(any())).thenReturn(apiKey);
        when(rateLimitService.tryAcquire(4L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gatewayService.forwardChatCompletion(
                        "Bearer test-key",
                        requestBody()
                )
        );

        assertEquals(429, exception.getStatusCode().value());
        verifyNoInteractions(routingService);
    }

    private DownstreamApiKey apiKey(
            Long id,
            int quotaAmount,
            int usedAmount
    ) {
        DownstreamApiKey apiKey = new DownstreamApiKey();
        apiKey.setId(id);
        apiKey.setStatus("ACTIVE");
        apiKey.setQuotaAmount(BigDecimal.valueOf(quotaAmount));
        apiKey.setUsedAmount(BigDecimal.valueOf(usedAmount));
        return apiKey;
    }

    private UpstreamChannel channel(Long id, int port) {
        UpstreamChannel channel = new UpstreamChannel();
        channel.setId(id);
        channel.setName("test-" + id);
        channel.setBaseUrl("http://127.0.0.1:" + port);
        channel.setModel("mock-model");
        channel.setWeight(1);
        channel.setStatus("ENABLED");
        return channel;
    }

    private JsonNode requestBody() throws IOException {
        return objectMapper.readTree(
                """
                {
                  "model": "mock-model",
                  "messages": [{"role": "user", "content": "hello"}]
                }
                """
        );
    }

    private HttpServer startServer(int status, String responseBody)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange ->
                respond(exchange, status, responseBody)
        );
        server.start();
        return server;
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String responseBody
    ) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add(
                "Content-Type",
                "application/json"
        );
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void stop(HttpServer server) {
        if (server != null) {
            server.stop(0);
        }
    }
}

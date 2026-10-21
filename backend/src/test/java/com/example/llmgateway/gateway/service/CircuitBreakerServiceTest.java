package com.example.llmgateway.gateway.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerServiceTest {

    @Test
    void shouldOpenAfterConsecutiveFailures() {
        AtomicLong now = new AtomicLong(1_000L);
        CircuitBreakerService service =
                new CircuitBreakerService(2, 30_000L, now::get);

        assertTrue(service.isAvailable(1L));
        service.recordFailure(1L);
        assertTrue(service.isAvailable(1L));
        service.recordFailure(1L);
        assertFalse(service.isAvailable(1L));
    }

    @Test
    void shouldAllowChannelAgainAfterOpenWindow() {
        AtomicLong now = new AtomicLong(1_000L);
        CircuitBreakerService service =
                new CircuitBreakerService(1, 30_000L, now::get);

        service.recordFailure(1L);
        assertFalse(service.isAvailable(1L));

        now.addAndGet(30_000L);
        assertTrue(service.isAvailable(1L));
    }

    @Test
    void shouldResetFailuresAfterSuccess() {
        AtomicLong now = new AtomicLong(1_000L);
        CircuitBreakerService service =
                new CircuitBreakerService(2, 30_000L, now::get);

        service.recordFailure(1L);
        service.recordSuccess(1L);
        service.recordFailure(1L);

        assertTrue(service.isAvailable(1L));
    }

    @Test
    void shouldKeepBreakersIndependentPerChannel() {
        AtomicLong now = new AtomicLong(1_000L);
        CircuitBreakerService service =
                new CircuitBreakerService(1, 30_000L, now::get);

        service.recordFailure(1L);

        assertFalse(service.isAvailable(1L));
        assertTrue(service.isAvailable(2L));
    }
}

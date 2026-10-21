package com.example.llmgateway.gateway.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {

    @Test
    void shouldLimitRequestsWithinWindow() {
        AtomicLong now = new AtomicLong(1_000L);
        RateLimitService service =
                new RateLimitService(2, 60_000L, now::get);

        assertTrue(service.tryAcquire(1L));
        assertTrue(service.tryAcquire(1L));
        assertFalse(service.tryAcquire(1L));
    }

    @Test
    void shouldResetAfterWindow() {
        AtomicLong now = new AtomicLong(1_000L);
        RateLimitService service =
                new RateLimitService(1, 60_000L, now::get);

        assertTrue(service.tryAcquire(1L));
        assertFalse(service.tryAcquire(1L));

        now.addAndGet(60_000L);
        assertTrue(service.tryAcquire(1L));
    }

    @Test
    void shouldKeepCountersIndependentPerKey() {
        AtomicLong now = new AtomicLong(1_000L);
        RateLimitService service =
                new RateLimitService(1, 60_000L, now::get);

        assertTrue(service.tryAcquire(1L));
        assertFalse(service.tryAcquire(1L));
        assertTrue(service.tryAcquire(2L));
    }

    @Test
    void shouldTreatZeroLimitAsUnlimited() {
        AtomicLong now = new AtomicLong(1_000L);
        RateLimitService service =
                new RateLimitService(0, 60_000L, now::get);

        assertTrue(service.tryAcquire(1L));
        assertTrue(service.tryAcquire(1L));
    }
}

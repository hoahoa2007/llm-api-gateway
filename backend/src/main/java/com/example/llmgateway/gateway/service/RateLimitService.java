package com.example.llmgateway.gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Service
public class RateLimitService {

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<Long, Window> windows =
            new ConcurrentHashMap<>();

    @Autowired
    public RateLimitService(
            @Value("${gateway.rate-limit.requests-per-minute:60}")
            int limit
    ) {
        this(limit, 60_000L, System::currentTimeMillis);
    }

    RateLimitService(int limit, long windowMillis, LongSupplier clock) {
        this.limit = Math.max(limit, 0);
        this.windowMillis = Math.max(windowMillis, 1);
        this.clock = clock;
    }

    public boolean tryAcquire(Long apiKeyId) {
        if (limit == 0) {
            return true;
        }

        long now = clock.getAsLong();
        long windowStart = now - Math.floorMod(now, windowMillis);
        Window window = windows.computeIfAbsent(
                apiKeyId,
                ignored -> new Window(windowStart)
        );

        synchronized (window) {
            if (window.start != windowStart) {
                window.start = windowStart;
                window.count = 0;
            }
            if (window.count >= limit) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private static final class Window {
        private long start;
        private int count;

        private Window(long start) {
            this.start = start;
        }
    }
}

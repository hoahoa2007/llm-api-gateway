package com.example.llmgateway.gateway.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Service
public class CircuitBreakerService {

    private final int failureThreshold;
    private final long openMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<Long, State> states =
            new ConcurrentHashMap<>();

    @Autowired
    public CircuitBreakerService(
            @Value("${gateway.circuit-breaker.failure-threshold:3}")
            int failureThreshold,
            @Value("${gateway.circuit-breaker.open-millis:30000}")
            long openMillis
    ) {
        this(failureThreshold, openMillis, System::currentTimeMillis);
    }

    CircuitBreakerService(
            int failureThreshold,
            long openMillis,
            LongSupplier clock
    ) {
        this.failureThreshold = Math.max(failureThreshold, 1);
        this.openMillis = Math.max(openMillis, 1);
        this.clock = clock;
    }

    public boolean isAvailable(Long channelId) {
        State state = states.get(channelId);
        if (state == null) {
            return true;
        }

        synchronized (state) {
            if (state.openUntilMillis == 0) {
                return true;
            }
            if (clock.getAsLong() >= state.openUntilMillis) {
                state.openUntilMillis = 0;
                state.consecutiveFailures = failureThreshold - 1;
                return true;
            }
            return false;
        }
    }

    public void recordSuccess(Long channelId) {
        State state = states.computeIfAbsent(
                channelId,
                ignored -> new State()
        );
        synchronized (state) {
            state.consecutiveFailures = 0;
            state.openUntilMillis = 0;
        }
    }

    public void recordFailure(Long channelId) {
        State state = states.computeIfAbsent(
                channelId,
                ignored -> new State()
        );
        synchronized (state) {
            if (state.openUntilMillis > 0) {
                return;
            }
            state.consecutiveFailures++;
            if (state.consecutiveFailures >= failureThreshold) {
                state.openUntilMillis = clock.getAsLong() + openMillis;
            }
        }
    }

    private static final class State {
        private int consecutiveFailures;
        private long openUntilMillis;
    }
}

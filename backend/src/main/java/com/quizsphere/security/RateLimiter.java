package com.quizsphere.security;

import com.quizsphere.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter kept in memory. Suitable for a single instance; a
 * multi-instance deployment should move this to a shared store (e.g. Redis).
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MS = 60_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public void check(String key, int maxPerMinute) {
        long now = System.currentTimeMillis();
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= maxPerMinute) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please wait a minute and try again.");
            }
            window.addLast(now);
        }
    }

    @Scheduled(fixedDelay = 300_000)
    void evictIdle() {
        long now = System.currentTimeMillis();
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || now - last > WINDOW_MS;
            }
        });
    }
}

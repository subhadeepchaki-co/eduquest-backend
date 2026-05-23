package com.abcd.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final int maxRequests;
    private final long windowMs;
    private final Map<String, Window> clients = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${ratelimit.max:5}") int maxRequests,
            @Value("${ratelimit.window:60000}") long windowMs
    ) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    public boolean isAllowed(String clientKey) {
        long now = System.currentTimeMillis();
        Window window = clients.compute(clientKey, (key, existing) -> {
            if (existing == null || now - existing.start > windowMs) {
                return new Window(now, 1);
            }
            existing.count++;
            return existing;
        });
        if (window.count > maxRequests) {
            log.warn("Rate limit exceeded for {}", clientKey);
            return false;
        }
        return true;
    }

    private static class Window {
        final long start;
        int count;

        Window(long start, int count) {
            this.start = start;
            this.count = count;
        }
    }
}
